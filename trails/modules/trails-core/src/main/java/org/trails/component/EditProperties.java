/*
 * Copyright 2004 Chris Nelson
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and limitations under the License.
 */
package org.trails.component;

import org.apache.tapestry.annotations.InjectObject;
import org.apache.tapestry.components.Block;
import org.trails.descriptor.BlockFinder;
import org.trails.descriptor.IPropertyDescriptor;

/*
 * Created on Sep 30, 2004
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */

/**
 * @author fus8882
 *         <p/>
 *         TODO To change the template for this generated type comment go to Window -
 *         Preferences - Java - Code Style - Code Templates
 */
public abstract class EditProperties extends ObjectEditComponent
{
	@InjectObject("spring:editorService")
	public abstract BlockFinder getBlockFinder();

	/**
	 * @return Returns the current property.
	 */
	public abstract IPropertyDescriptor getProperty();

	public Block getBlock()
	{
		return getBlockFinder().findBlock(getPage().getRequestCycle(), getProperty());
	}
}