/*******************************************************************************
 * Copyright (c) 2003 International Business Machines Corp. and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v0.5 
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v05.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 ******************************************************************************/
package org.eclipse.jdt.internal.corext.refactoring.participants;

import org.eclipse.jdt.internal.corext.Assert;

public abstract class MoveParticipant implements IMoveParticipant {

	private IRefactoringProcessor fProcessor;
	private Object fTarget;
	
	public void initialize(IRefactoringProcessor processor) {
		Assert.isNotNull(processor);
		fProcessor= processor;
	}
	
	public IRefactoringProcessor getProcessor() {
		return fProcessor;
	}
	
	public Object getTarget() {
		return fTarget;
	}

	public void setTarget(Object target) {
		fTarget= target;
	}
}
