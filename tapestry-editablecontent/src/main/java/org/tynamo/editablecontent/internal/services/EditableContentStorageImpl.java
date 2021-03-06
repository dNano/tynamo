package org.tynamo.editablecontent.internal.services;

import java.util.Date;
import java.util.List;
import java.util.concurrent.ConcurrentMap;

import javax.persistence.EntityManager;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Root;
import javax.servlet.http.HttpServletRequest;

import org.apache.tapestry5.ioc.annotations.Inject;
import org.apache.tapestry5.ioc.annotations.Symbol;
import org.apache.tapestry5.ioc.services.ThreadLocale;
import org.apache.tapestry5.jpa.EntityManagerManager;
import org.tynamo.editablecontent.EditableContentSymbols;
import org.tynamo.editablecontent.entities.RevisionedContent;
import org.tynamo.editablecontent.entities.TextualContent;
import org.tynamo.editablecontent.services.EditableContentStorage;

import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap;

public class EditableContentStorageImpl implements EditableContentStorage {
	// private EntityManager entityManager;
	private ThreadLocale threadLocale;
	private HttpServletRequest request;
	private ConcurrentMap<String, String> lruCache;
	private String persistenceUnitName;
	private EntityManagerManager entityManagerManager;

	public EditableContentStorageImpl(EntityManagerManager entityManagerManager, ThreadLocale threadLocale,
		HttpServletRequest request, @Inject @Symbol(EditableContentSymbols.LOCALIZED_CONTENT) boolean localizedContent,
		@Inject @Symbol(EditableContentSymbols.PERSISTENCEUNIT) String persistenceUnitName,
		@Inject @Symbol(EditableContentSymbols.LRU_CACHE_SIZE) int lruCacheSize) {

		EntityManager entityManager = null;
		if (persistenceUnitName.isEmpty()) {
			if (entityManagerManager.getEntityManagers().size() != 1)
				throw new IllegalArgumentException(
					"You have to specify the persistenceunit for editable content if multiple persistence units are configured in the system. Contribute a value for EditableContentSymbols.PERSISTENCEUNIT");
			entityManager = entityManagerManager.getEntityManagers().values().iterator().next();
		} else {
			entityManager = entityManagerManager.getEntityManager(persistenceUnitName);
			if (entityManager == null)
				throw new IllegalArgumentException(
					"Persistence unit '"
						+ persistenceUnitName
						+ "' is configured for editable content, but it was not found. Check that the contributed name matches with persistenceunit configuration");
		}
		this.entityManagerManager = entityManagerManager;
		// entityManagerManager doesn't not give us a shadow for entityManager but the actual per-thread object so we cannot save a reference to
		// the entityManager
		this.persistenceUnitName = persistenceUnitName;
		this.threadLocale = localizedContent ? threadLocale : null;
		this.request = request;
		lruCache = lruCacheSize <= 0 ? null : new ConcurrentLinkedHashMap.Builder<String, String>()
			.maximumWeightedCapacity(lruCacheSize).build();
	}

	EntityManager getEntityManager() {
		return persistenceUnitName.isEmpty() ? entityManagerManager.getEntityManagers().values().iterator().next()
			: entityManagerManager.getEntityManager(persistenceUnitName);
	}

	private String localizeContentId(final String contentId) {
		if (threadLocale == null || threadLocale.getLocale() == null) return contentId;
		return contentId + "_" + threadLocale.getLocale().toString();
	}

	@Override
	public boolean contains(String contentId) {
		EntityManager entityManager = getEntityManager();
		contentId = localizeContentId(contentId);
		CriteriaBuilder qb = entityManager.getCriteriaBuilder();
		CriteriaQuery<Long> cq = qb.createQuery(Long.class);
		Root<?> from = cq.from(TextualContent.class);
		cq.select(qb.count(from));
		cq.where(qb.equal(from.get("id"), contentId));
		return entityManager.createQuery(cq).getSingleResult() > 0 ? true : false;
	}

	void createRevision(TextualContent content, int maxHistory) {
		EntityManager entityManager = getEntityManager();
		RevisionedContent revision = new RevisionedContent(content);
		entityManager.persist(revision);
		// delete revisions earlier than max allowed. If maxHistory is 0, keep all revisions
		if (maxHistory > 0) {
			CriteriaBuilder builder = entityManager.getCriteriaBuilder();
			CriteriaQuery<RevisionedContent> query = builder.createQuery(RevisionedContent.class);
			// typically, we should be deleting only max one at a time, but this still shouldn't
			// be performing that badly as the entities are lazily fetched
			Root<?> from = query.from(RevisionedContent.class);
			query.where(builder.equal(from.get("id"), content.getId()));
			query.orderBy(builder.desc(from.get("revision")));
			List<RevisionedContent> revisions = entityManager.createQuery(query).getResultList();
			int size = revisions.size();
			for (int i = 0; size - i > maxHistory; i++)
				entityManager.remove(revisions.get(i));
		}
	}

	@Override
	public String updateContent(String contentId, String contentValue, int maxHistory) {
		EntityManager entityManager = getEntityManager();
		contentId = localizeContentId(contentId);
		TextualContent content = entityManager.find(TextualContent.class, contentId);
		if (content == null) {
			content = new TextualContent();
			content.setId(contentId);
		} else {
			// store the older version as a previous version to revisionedContent
			if (maxHistory >= 0) createRevision(content, maxHistory);
		}
		content.setValue(contentValue);
		content.setLastModified(new Date());
		String author = request.getRemoteUser();
		if (author == null) author = request.getRemoteAddr();
		content.setAuthor(author);

		// persist previous version
		entityManager.persist(content);
		// nullify the cache here rather than add the value, just to reset the possibly stale cache
		if (lruCache != null) lruCache.remove(contentId);
		return null;
	}

	@Override
	public String getTextualContentValue(String contentId) {
		EntityManager entityManager = getEntityManager();
		contentId = localizeContentId(contentId);
		String value = lruCache == null ? null : lruCache.get(contentId);
		if (value == null) {
			TextualContent content = entityManager.find(TextualContent.class, contentId);
			value = content.getValue();
			lruCache.put(contentId, value);
		}
		return value;
	}

	@Override
	public TextualContent getTextualContent(String contentId) {
		return getEntityManager().find(TextualContent.class, localizeContentId(contentId));
	}

}
