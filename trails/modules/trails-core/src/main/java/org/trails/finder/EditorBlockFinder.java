package org.trails.finder;

import org.apache.tapestry.IRequestCycle;
import org.apache.tapestry.components.Block;
import org.trails.page.IEditorBlockPage;
import org.trails.descriptor.IPropertyDescriptor;


public class EditorBlockFinder extends BaseBlockFinder
{
	public EditorBlockFinder()
	{
		super();
		// TODO Auto-generated constructor stub
	}

	@Override
	public Block findBlock(IRequestCycle cycle, IPropertyDescriptor descriptor)
	{

		Block block = super.findBlock(cycle, descriptor);
		((IEditorBlockPage) block.getPage()).setModel(((IEditorBlockPage) cycle.getPage()).getModel());
		return block;
	}


}