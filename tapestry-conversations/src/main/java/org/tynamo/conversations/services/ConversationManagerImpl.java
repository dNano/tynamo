package org.tynamo.conversations.services;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.servlet.http.HttpServletRequest;

import org.apache.tapestry5.EventContext;
import org.apache.tapestry5.services.ComponentEventRequestParameters;
import org.apache.tapestry5.services.Cookies;
import org.apache.tapestry5.services.PageRenderRequestParameters;
import org.apache.tapestry5.services.Request;
import org.tynamo.conversations.ConversationAware;

public class ConversationManagerImpl implements ConversationManager {
	// protected so you can override toString() in case user application already uses the same keys
	// for something else
	protected enum Keys {
		_conversationId, conversations
	};

	private final Request request;

	private final Cookies cookies;

	private ConversationalPersistentFieldStrategy pagePersistentFieldStrategy;

	private HttpServletRequest servletRequest;
	
	private Map<String, List<ConversationAware>> conversationAwareListeners = Collections.synchronizedMap(new HashMap<String, List<ConversationAware>>());

	public ConversationManagerImpl(Request request, HttpServletRequest servletRequest, Cookies cookies, Map<Class,ConversationAware> listeners) {
		this.request = request;
		this.cookies = cookies;
		this.servletRequest = servletRequest;
		for (Entry<Class,ConversationAware> entry : listeners.entrySet() ) {
			String pageName = entry.getKey().getSimpleName();
			addConversationListener(pageName, entry.getValue());
		}
	}

	@SuppressWarnings("unchecked")
	protected Map<String, Conversation> getConversations() {
		Map<String, Conversation> conversations = (Map<String, Conversation>) request.getSession(true).getAttribute(Keys.conversations.toString());
		if (conversations == null) {
			conversations = Collections.synchronizedMap(new HashMap<String, Conversation>());
			request.getSession(true).setAttribute(Keys.conversations.toString(), conversations);
		}
		return conversations;
	}

	public boolean activateConversation(Object parameterObject) {
		if (parameterObject == null) return false;
		EventContext activationContext = null;
		String pageName = null;
		if (parameterObject instanceof PageRenderRequestParameters) {
			activationContext = ((PageRenderRequestParameters) parameterObject).getActivationContext();
			pageName = ((PageRenderRequestParameters) parameterObject).getLogicalPageName();
		} else if (parameterObject instanceof ComponentEventRequestParameters) {
			activationContext = ((ComponentEventRequestParameters) parameterObject).getPageActivationContext();
			pageName = ((ComponentEventRequestParameters) parameterObject).getActivePageName();
		}

		String conversationId = null;

		// Try reading the conversation id from a cookie first
		try {
			conversationId = cookies.readCookieValue(pageName.toLowerCase() + ConversationManagerImpl.Keys._conversationId);
			Conversation conversation = getConversations().get(conversationId);
			if (conversation == null) conversationId = null;
			else if (!conversation.isUsingCookie()) conversationId = null;
		} catch (NumberFormatException e) {
			// Ignore
		}
		// If cookie-based conversation isn't available, try activation context
		if (conversationId == null) if (activationContext != null) try {
			conversationId = activationContext.get(String.class, activationContext.getCount() - 1);
		} catch (RuntimeException e) {
			// Ignore
		}

		return activate(endConversationIfIdle(conversationId));
	}

	private boolean activate(Conversation conversation) {
		if (conversation == null) return false;
		request.setAttribute(Keys._conversationId.toString(), conversation.getId());
		return true;
	}

	public String createConversation(String pageName, Integer maxIdleSeconds) {
		return createConversation(pageName, maxIdleSeconds, false);
	}

	public String createConversation(String pageName, Integer maxIdleSeconds, boolean useCookie) {
		return createConversation(String.valueOf(System.currentTimeMillis()), pageName, maxIdleSeconds, 0, useCookie);
	}

	public String createConversation(String pageName, Integer maxIdleSeconds, Integer maxConversationLengthSeconds, boolean useCookie) {
		return createConversation(String.valueOf(System.currentTimeMillis()), pageName, maxIdleSeconds, maxConversationLengthSeconds, useCookie);
	}

	public String createConversation(String id, String pageName, Integer maxIdleSeconds, Integer maxConversationLengthSeconds, boolean useCookie) {
		pageName = pageName == null ? "" : pageName;
		// Don't use path in a cookie, it's actually relatively difficult to find out from here
		if (useCookie) cookies.writeCookieValue(pageName.toLowerCase() + Keys._conversationId.toString(), String.valueOf(id));
		Conversation conversation = new Conversation(servletRequest.getSession(true).getId(), id, pageName, maxIdleSeconds, maxConversationLengthSeconds, useCookie);
		endIdleConversations();
		getConversations().put(id, conversation);
		activate(conversation);
		if (conversationAwareListeners.containsKey(conversation.getPageName())) {
			List<ConversationAware> conversationListeners = conversationAwareListeners.get(conversation.getPageName());
			for (ConversationAware conversationAware : conversationListeners) conversationAware.onConversationCreated(conversation);
		}
		return id;
	}

	public void endIdleConversations() {
		Iterator<Conversation> iterator = getConversations().values().iterator();
		while (iterator.hasNext()) {
			Conversation conversation = iterator.next();
			if (conversation.isIdle(false)) {
				discardConversation(conversation, true);
				iterator.remove();
			}
		}
	}

	protected void discardConversation(Conversation conversation, boolean expired) {
		if (conversation == null) return;
		// Notify conversation ending
		if (conversationAwareListeners.containsKey(conversation.getPageName())) {
			List<ConversationAware> conversationListeners = conversationAwareListeners.get(conversation.getPageName());
			for (ConversationAware conversationAware : conversationListeners) conversationAware.onConversationEnded(conversation, expired);
		}
		//discardConversation
		if (conversation.isUsingCookie()) cookies.removeCookieValue(String.valueOf(conversation.getId()));
		if (pagePersistentFieldStrategy != null) pagePersistentFieldStrategy.discardChanges(conversation.getPageName());
	}

	public boolean exists(String conversationId) {
		Conversation conversation = getConversations().get(conversationId);
		if (conversation == null) return false;
		else return true;
	}

	protected Conversation endConversationIfIdle(String conversationId) {
		Conversation conversation = getConversations().get(conversationId);
		if (conversation == null) return null;
		boolean resetTimeout = !("false".equals(request.getParameter(Parameters.keepalive.name())));
		if (conversation.isIdle(resetTimeout)) {
			discardConversation(conversation, true);
			getConversations().remove(conversation.getId());
			conversationId = null;
		}
		return conversation;
	}

	public String getActiveConversation() {
		String conversationId = (String) request.getAttribute(Keys._conversationId.toString());
		if (conversationId == null) return null;
		return exists(conversationId) ? conversationId : null;
	}

	public int getSecondsBeforeActiveConversationBecomesIdle() {
		String conversationId = getActiveConversation();
		if (conversationId == null) return -1;
		Conversation conversation = getConversations().get(conversationId);
		if (conversation == null) return -1;
		return conversation.getSecondsBeforeBecomesIdle();
	}

	public void setPagePersistentFieldStrategy(ConversationalPersistentFieldStrategy pagePersistentFieldStrategy) {
		this.pagePersistentFieldStrategy = pagePersistentFieldStrategy;
	}

	public boolean isActiveConversation(String conversationId) {
		if (conversationId == null) return false;
		return conversationId.equals(getActiveConversation());
	}

	public String endConversation(String conversationId) {
		Conversation conversation = getConversations().get(conversationId);
		if (conversation == null) return null;
		discardConversation(conversation, false);
		getConversations().remove(conversation.getId());
		return null;
	}

	public void addConversationListener(String pageName, ConversationAware conversationAware) {
		List<ConversationAware> conversationListenersForPage = conversationAwareListeners.get(pageName);
		if (conversationListenersForPage == null) {
			conversationListenersForPage = Collections.synchronizedList(new ArrayList<ConversationAware>() );
			conversationAwareListeners.put(pageName, conversationListenersForPage);
		}
		conversationListenersForPage.add(conversationAware);
	}

	public void removeConversationListener(String pageName, ConversationAware conversationAware) {
		List<ConversationAware> conversationListenersForPage = conversationAwareListeners.get(pageName);
		if (conversationListenersForPage == null) return;
		conversationListenersForPage.remove(conversationAware);
	}
}
