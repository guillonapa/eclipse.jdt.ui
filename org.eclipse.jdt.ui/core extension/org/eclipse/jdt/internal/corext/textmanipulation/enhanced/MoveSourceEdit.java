/*******************************************************************************
 * Copyright (c) 2002 International Business Machines Corp. and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0 
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 ******************************************************************************/
package org.eclipse.jdt.internal.corext.textmanipulation.enhanced;

import java.util.List;

import org.eclipse.jface.text.DocumentEvent;

import org.eclipse.core.runtime.CoreException;

import org.eclipse.jdt.internal.corext.Assert;

public final class MoveSourceEdit extends AbstractTransferEdit {

	private String fContent;
	/* package */ int fCounter;
	private MoveTargetEdit fTarget;
	
	public MoveSourceEdit(int offset, int length) {
		super(new TextRange(offset, length));
	}

	public MoveSourceEdit(int offset, int length, MoveTargetEdit target) {
		this(offset, length);
		setTargetEdit(target);
	}

	private MoveSourceEdit(TextRange range) {
		super(range);
	}
	
	public void setTargetEdit(MoveTargetEdit edit) {
		if (fTarget != edit) {
			fTarget= edit;
			fTarget.setSourceEdit(this);
		}
	}
	
	/* non Java-doc
	 * @see TextEdit#perform
	 */	
	public void perform(TextBuffer buffer) throws CoreException {
		TextRange range= getTextRange();
		fContent= buffer.getContent(range.getOffset(), range.getLength());
		if (++fCounter == 2) {
			try {
				// Delete source
				if (!getTextRange().isDeleted()) {
					fMode= DELETE;
					buffer.replace(range, "");
				}
				
				if (!fTarget.getTextRange().isDeleted()) {
					// Insert target
					fMode= INSERT;
					buffer.replace(fTarget.getTextRange(), fContent);
				}
			} finally {
				fMode= UNDEFINED;
				clearContent();
			}
		}
	}

	/* non Java-doc
	 * @see TextEdit#copy
	 */	
	public TextEdit copy0() {
		return new MoveSourceEdit(getTextRange().copy());
	}

	/* package */ String getContent() {
		return fContent;
	}
	
	/* package */ void clearContent() {
		fContent= null;
	}
	
	/* package */ void updateTextRange(int delta, List executedEdits) {
		if (fMode == DELETE) {
			predecessorExecuted(getSuccessorIterator(), delta);
			getTextRange().adjustLength(delta);
			updateParents(delta);
		} else if (fMode == INSERT) {
			predecessorExecuted(fTarget.getSuccessorIterator(), delta);
			fTarget.getTextRange().adjustLength(delta);
			fTarget.updateParents(delta);
			
			markAsDeleted(fTarget.getChildren());
			
			List children= getChildren();
			setChildren(null);
			int moveDelta= fTarget.getTextRange().getOffset() - getTextRange().getOffset();
			move(children, moveDelta);
			fTarget.setChildren(children);
		} else {
			Assert.isTrue(false);
		}
	}
	
	/* package */ void checkRange(DocumentEvent event) {
		if (fMode == INSERT) {
			fTarget.checkRange(event);
		} else  {
			super.checkRange(event);
		}
	}
	
	/* package */ boolean checkEdit(int bufferLength) {
		return fTarget != null && fTarget.getSourceEdit() == this && super.checkEdit(bufferLength);
	}
	
	/* package */ MoveTargetEdit getTargetEdit() {
		return fTarget;
	}
}
