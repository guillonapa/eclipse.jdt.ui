/*******************************************************************************
 * Copyright (c) 2005, 2011 IBM Corporation and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.corext.refactoring.scripting;

import java.util.Map;

import org.eclipse.core.runtime.CoreException;

import org.eclipse.ltk.core.refactoring.Refactoring;
import org.eclipse.ltk.core.refactoring.RefactoringDescriptor;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;

import org.eclipse.jdt.core.refactoring.descriptors.JavaRefactoringDescriptor;

import org.eclipse.jdt.internal.core.refactoring.descriptors.RefactoringSignatureDescriptorFactory;
import org.eclipse.jdt.internal.corext.refactoring.JavaRefactoringArguments;
import org.eclipse.jdt.internal.corext.refactoring.code.InlineTempRefactoring;

/**
 * Refactoring contribution for the inline temp refactoring.
 *
 * @since 3.2
 */
public final class InlineTempRefactoringContribution extends JavaUIRefactoringContribution {

	@Override
	public final Refactoring createRefactoring(JavaRefactoringDescriptor descriptor, RefactoringStatus status) throws CoreException {
		JavaRefactoringArguments arguments= new JavaRefactoringArguments(descriptor.getProject(), retrieveArgumentMap(descriptor));
		return new InlineTempRefactoring(arguments, status);
	}

	@Override
	public RefactoringDescriptor createDescriptor() {
		return RefactoringSignatureDescriptorFactory.createInlineLocalVariableDescriptor();
	}

	@Override
	public RefactoringDescriptor createDescriptor(String id, String project, String description, String comment, Map<String, String> arguments, int flags) {
		return RefactoringSignatureDescriptorFactory.createInlineLocalVariableDescriptor(project, description, comment, arguments, flags);
	}
}
