/*******************************************************************************
 * Copyright (c) 2000, 2004 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Renaud Waldura &lt;renaud+eclipse@waldura.com&gt; - New class/interface with wizard
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.text.correction;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

import org.eclipse.core.runtime.CoreException;

import org.eclipse.swt.graphics.Image;

import org.eclipse.ui.ISharedImages;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.Signature;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTMatcher;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ArrayType;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.CastExpression;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ConstructorInvocation;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.IPackageBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.ParameterizedType;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.QualifiedType;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SimpleType;
import org.eclipse.jdt.core.dom.StructuralPropertyDescriptor;
import org.eclipse.jdt.core.dom.SuperConstructorInvocation;
import org.eclipse.jdt.core.dom.SuperFieldAccess;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;
import org.eclipse.jdt.core.dom.SwitchCase;
import org.eclipse.jdt.core.dom.SwitchStatement;
import org.eclipse.jdt.core.dom.ThisExpression;
import org.eclipse.jdt.core.dom.ThrowStatement;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.TypeParameter;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.WildcardType;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

import org.eclipse.jdt.internal.corext.codemanipulation.ImportRewrite;
import org.eclipse.jdt.internal.corext.codemanipulation.StubUtility;
import org.eclipse.jdt.internal.corext.dom.ASTNodeFactory;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.dom.Bindings;
import org.eclipse.jdt.internal.corext.dom.ScopeAnalyzer;
import org.eclipse.jdt.internal.corext.dom.TypeRules;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.jdt.internal.corext.util.TypeFilter;

import org.eclipse.jdt.ui.JavaElementImageDescriptor;
import org.eclipse.jdt.ui.text.java.IInvocationContext;
import org.eclipse.jdt.ui.text.java.IProblemLocation;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.JavaPluginImages;
import org.eclipse.jdt.internal.ui.javaeditor.ASTProvider;
import org.eclipse.jdt.internal.ui.text.correction.ChangeMethodSignatureProposal.ChangeDescription;
import org.eclipse.jdt.internal.ui.text.correction.ChangeMethodSignatureProposal.EditDescription;
import org.eclipse.jdt.internal.ui.text.correction.ChangeMethodSignatureProposal.InsertDescription;
import org.eclipse.jdt.internal.ui.text.correction.ChangeMethodSignatureProposal.RemoveDescription;
import org.eclipse.jdt.internal.ui.text.correction.ChangeMethodSignatureProposal.SwapDescription;
import org.eclipse.jdt.internal.ui.viewsupport.JavaElementImageProvider;

public class UnresolvedElementsSubProcessor {
	
	public static void getVariableProposals(IInvocationContext context, IProblemLocation problem, Collection proposals) throws CoreException {
		
		ICompilationUnit cu= context.getCompilationUnit();
		
		CompilationUnit astRoot= context.getASTRoot();
		ASTNode selectedNode= problem.getCoveredNode(astRoot);
		if (selectedNode == null) {
			return;
		}
		
		// type that defines the variable
		ITypeBinding binding= null;
		ITypeBinding declaringTypeBinding= Bindings.getBindingOfParentType(selectedNode);
		if (declaringTypeBinding == null) {
			return;
		}
		

		// possible type kind of the node
		boolean suggestVariableProposals= true;
		int typeKind= 0;
		
		while (selectedNode instanceof ParenthesizedExpression) {
			selectedNode= ((ParenthesizedExpression) selectedNode).getExpression();
		}
		
		
		Name node= null;

		switch (selectedNode.getNodeType()) {
			case ASTNode.SIMPLE_NAME:
				node= (SimpleName) selectedNode;
				ASTNode parent= node.getParent();
				StructuralPropertyDescriptor locationInParent= node.getLocationInParent();
				if (locationInParent == MethodInvocation.EXPRESSION_PROPERTY) {
					typeKind= SimilarElementsRequestor.CLASSES;
				} else if (locationInParent == FieldAccess.NAME_PROPERTY) {
					Expression expression= ((FieldAccess) parent).getExpression();
					if (expression != null) {
						binding= expression.resolveTypeBinding();
						if (binding == null) {
							node= null;
						}
					}
				} else if (parent instanceof SimpleType) {
					suggestVariableProposals= false;
					typeKind= SimilarElementsRequestor.REF_TYPES;
				} else if (parent instanceof QualifiedName) {
					Name qualifier= ((QualifiedName) parent).getQualifier();
					if (qualifier != node) {
						binding= qualifier.resolveTypeBinding();
					} else {
						typeKind= SimilarElementsRequestor.REF_TYPES;
					}
					ASTNode outerParent= parent.getParent();
					while (outerParent instanceof QualifiedName) {
						outerParent= outerParent.getParent();
					}
					if (outerParent instanceof SimpleType) {
						typeKind= SimilarElementsRequestor.REF_TYPES;
						suggestVariableProposals= false;
					}
				} else if (locationInParent == SwitchCase.EXPRESSION_PROPERTY) {
					ITypeBinding switchExp= ((SwitchStatement) node.getParent().getParent()).getExpression().resolveTypeBinding();
					if (switchExp != null && switchExp.isEnum()) {
						binding= switchExp;
					}
				}
				break;		
			case ASTNode.QUALIFIED_NAME:
				QualifiedName qualifierName= (QualifiedName) selectedNode;
				ITypeBinding qualifierBinding= qualifierName.getQualifier().resolveTypeBinding();
				if (qualifierBinding != null) {
					node= qualifierName.getName();
					binding= qualifierBinding;
				} else {
					node= qualifierName.getQualifier();
					typeKind= SimilarElementsRequestor.REF_TYPES;
					suggestVariableProposals= node.isSimpleName();
				}
				if (selectedNode.getParent() instanceof SimpleType) {
					typeKind= SimilarElementsRequestor.REF_TYPES;
					suggestVariableProposals= false;
				}
				break;		
			case ASTNode.FIELD_ACCESS:
				FieldAccess access= (FieldAccess) selectedNode;
				Expression expression= access.getExpression();
				if (expression != null) {
					binding= expression.resolveTypeBinding();
					if (binding != null) {
						node= access.getName();
					}
				}
				break;
			case ASTNode.SUPER_FIELD_ACCESS:
				binding= declaringTypeBinding.getSuperclass();
				node= ((SuperFieldAccess) selectedNode).getName();
				break;
			default:
		}
		
		if (node == null) {
			return;
		}
		

		// add type proposals
		if (typeKind != 0) {
			int relevance= Character.isUpperCase(ASTNodes.getSimpleNameIdentifier(node).charAt(0)) ? 3 : 0;
			addSimilarTypeProposals(typeKind, cu, node, relevance + 1, proposals);
			addNewTypeProposals(cu, node, SimilarElementsRequestor.REF_TYPES, relevance, proposals);
		}
		
		if (!suggestVariableProposals) {
			return;
		}
		
		SimpleName simpleName= node.isSimpleName() ? (SimpleName) node : ((QualifiedName) node).getName();
		boolean isWriteAccess= ASTResolving.isWriteAccess(node);
		
		// similar variables
		addSimilarVariableProposals(cu, astRoot, binding, simpleName, isWriteAccess, proposals);	
		
		// new fields
		addNewFieldProposals(cu, astRoot, binding, declaringTypeBinding, simpleName, isWriteAccess, proposals);
		
		// new parameters and local variables
		if (binding == null) {
			addNewVariableProposals(cu, node, simpleName, proposals);
		}
	}
	
	private static void addNewVariableProposals(ICompilationUnit cu, Name node, SimpleName simpleName, Collection proposals) {
		String name= simpleName.getIdentifier();
		BodyDeclaration bodyDeclaration= ASTResolving.findParentBodyDeclaration(node);
		int type= bodyDeclaration.getNodeType();
		if (type == ASTNode.METHOD_DECLARATION) {
			int relevance= StubUtility.hasParameterName(cu.getJavaProject(), name) ? 8 : 5;
			String label= CorrectionMessages.getFormattedString("UnresolvedElementsSubProcessor.createparameter.description", simpleName.getIdentifier()); //$NON-NLS-1$
			Image image= JavaPluginImages.get(JavaPluginImages.IMG_CORRECTION_LOCAL);
			proposals.add(new NewVariableCompletionProposal(label, cu, NewVariableCompletionProposal.PARAM, simpleName, null, relevance, image));
		}
		if (type == ASTNode.INITIALIZER || (type == ASTNode.METHOD_DECLARATION && !ASTResolving.isInsideConstructorInvocation((MethodDeclaration) bodyDeclaration, node))) {
			int relevance= StubUtility.hasLocalVariableName(cu.getJavaProject(), name) ? 10 : 7;
			String label= CorrectionMessages.getFormattedString("UnresolvedElementsSubProcessor.createlocal.description", simpleName.getIdentifier()); //$NON-NLS-1$
			Image image= JavaPluginImages.get(JavaPluginImages.IMG_CORRECTION_LOCAL);
			proposals.add(new NewVariableCompletionProposal(label, cu, NewVariableCompletionProposal.LOCAL, simpleName, null, relevance, image));
		}
		
		if (node.getParent().getNodeType() == ASTNode.ASSIGNMENT) {
			Assignment assignment= (Assignment) node.getParent();
			if (assignment.getLeftHandSide() == node && assignment.getParent().getNodeType() == ASTNode.EXPRESSION_STATEMENT) {
				ASTNode statement= assignment.getParent();
				ASTRewrite rewrite= ASTRewrite.create(statement.getAST());
				if (ASTNodes.isControlStatementBody(assignment.getParent().getLocationInParent())) {
					rewrite.replace(statement, rewrite.getAST().newBlock(), null);
				} else {
					rewrite.remove(statement, null);
				}
				String label= CorrectionMessages.getString("UnresolvedElementsSubProcessor.removestatement.description"); //$NON-NLS-1$
				Image image= JavaPlugin.getDefault().getWorkbench().getSharedImages().getImage(ISharedImages.IMG_TOOL_DELETE);
				ASTRewriteCorrectionProposal proposal= new ASTRewriteCorrectionProposal(label, cu, rewrite, 4, image);
				proposals.add(proposal);
			}
		}
	}

	private static void addNewFieldProposals(ICompilationUnit cu, CompilationUnit astRoot, ITypeBinding binding, ITypeBinding declaringTypeBinding, SimpleName simpleName, boolean isWriteAccess, Collection proposals) throws JavaModelException {
		// new variables
		ICompilationUnit targetCU;
		ITypeBinding senderDeclBinding;
		if (binding != null) {
			senderDeclBinding= binding.getTypeDeclaration();
			targetCU= ASTResolving.findCompilationUnitForBinding(cu, astRoot, senderDeclBinding);
		} else { // binding is null for accesses without qualifier
			senderDeclBinding= declaringTypeBinding;
			targetCU= cu;
		}
		
		if (!senderDeclBinding.isFromSource() || targetCU == null || !JavaModelUtil.isEditable(targetCU)) {
			return;
		}
		
		addNewFieldForType(targetCU, binding, senderDeclBinding, simpleName, isWriteAccess, proposals);
			
		if (binding == null && senderDeclBinding.isNested()) {
			ASTNode anonymDecl= astRoot.findDeclaringNode(senderDeclBinding);
			if (anonymDecl != null) {
				ITypeBinding bind= Bindings.getBindingOfParentType(anonymDecl.getParent());
				if (!bind.isAnonymous()) {
					addNewFieldForType(targetCU, binding, bind, simpleName, isWriteAccess, proposals);
				}
			}
		}
	}

	private static void addNewFieldForType(ICompilationUnit targetCU, ITypeBinding binding, ITypeBinding senderDeclBinding, SimpleName simpleName, boolean isWriteAccess, Collection proposals) {
		String name= simpleName.getIdentifier();
		String label;
		Image image;
		if (senderDeclBinding.isEnum() && !isWriteAccess) {
			label= CorrectionMessages.getFormattedString("UnresolvedElementsSubProcessor.createenum.description", new Object[] { name, ASTResolving.getTypeSignature(senderDeclBinding) }); //$NON-NLS-1$
			image= JavaPluginImages.get(JavaPluginImages.IMG_FIELD_PUBLIC);
			proposals.add(new NewVariableCompletionProposal(label, targetCU, NewVariableCompletionProposal.ENUM_CONST, simpleName, senderDeclBinding, 10, image));
		} else {
			if (binding == null) {
				label= CorrectionMessages.getFormattedString("UnresolvedElementsSubProcessor.createfield.description", name); //$NON-NLS-1$
				image= JavaPluginImages.get(JavaPluginImages.IMG_FIELD_PRIVATE);
			} else {
				label= CorrectionMessages.getFormattedString("UnresolvedElementsSubProcessor.createfield.other.description", new Object[] { name, ASTResolving.getTypeSignature(senderDeclBinding) } ); //$NON-NLS-1$
				image= JavaPluginImages.get(JavaPluginImages.IMG_FIELD_PUBLIC);
			}
			int fieldRelevance= StubUtility.hasFieldName(targetCU.getJavaProject(), name) ? 9 : 6;
			proposals.add(new NewVariableCompletionProposal(label, targetCU, NewVariableCompletionProposal.FIELD, simpleName, senderDeclBinding, fieldRelevance, image));

			if (!isWriteAccess && !senderDeclBinding.isAnonymous()) {
				if (binding == null) {
					label= CorrectionMessages.getFormattedString("UnresolvedElementsSubProcessor.createconst.description", name); //$NON-NLS-1$
					image= JavaPluginImages.get(JavaPluginImages.IMG_FIELD_PRIVATE);
				} else {
					label= CorrectionMessages.getFormattedString("UnresolvedElementsSubProcessor.createconst.other.description", new Object[] { name, ASTResolving.getTypeSignature(senderDeclBinding) } ); //$NON-NLS-1$
					image= JavaPluginImages.get(JavaPluginImages.IMG_FIELD_PUBLIC);
				}
				int constRelevance= StubUtility.hasConstantName(name) ? 9 : 4;
				proposals.add(new NewVariableCompletionProposal(label, targetCU, NewVariableCompletionProposal.CONST_FIELD, simpleName, senderDeclBinding, constRelevance, image));
			}	
		}
	}
	
	private static void addSimilarVariableProposals(ICompilationUnit cu, CompilationUnit astRoot, ITypeBinding binding, SimpleName node, boolean isWriteAccess, Collection proposals) {
		int kind= ScopeAnalyzer.VARIABLES | ScopeAnalyzer.CHECK_VISIBILITY;
		if (!isWriteAccess) {
			kind |= ScopeAnalyzer.METHODS; // also try to find similar methods
		}
		
		IBinding[] varsAndMethodsInScope= (new ScopeAnalyzer(astRoot)).getDeclarationsInScope(node, kind);
		if (varsAndMethodsInScope.length > 0) {
			// avoid corrections like int i= i;
			String otherNameInAssign= null;
			
			// help with x.getString() -> y.getString()
			String methodSenderName= null;
			String fieldSenderName= null;
			
			ASTNode parent= node.getParent();
			switch (parent.getNodeType()) {
			case ASTNode.VARIABLE_DECLARATION_FRAGMENT:
				// node must be initializer
				otherNameInAssign= ((VariableDeclarationFragment) parent).getName().getIdentifier();
				break;
			case ASTNode.ASSIGNMENT:
				Assignment assignment= (Assignment) parent;
				if (isWriteAccess && assignment.getRightHandSide() instanceof SimpleName) {
					otherNameInAssign= ((SimpleName) assignment.getRightHandSide()).getIdentifier();
				} else if (!isWriteAccess && assignment.getLeftHandSide() instanceof SimpleName) {
					otherNameInAssign= ((SimpleName) assignment.getLeftHandSide()).getIdentifier();
				}
				break;
			case ASTNode.METHOD_INVOCATION:
				MethodInvocation inv= (MethodInvocation) parent;
				if (inv.getExpression() == node) {
					methodSenderName= inv.getName().getIdentifier();
				}
				break;
			case ASTNode.QUALIFIED_NAME:
				QualifiedName qualName= (QualifiedName) parent;
				if (qualName.getQualifier() == node) {
					fieldSenderName= qualName.getName().getIdentifier();
				}
				break;
			}
				
			
			ITypeBinding guessedType= ASTResolving.guessBindingForReference(node);

			ITypeBinding objectBinding= astRoot.getAST().resolveWellKnownType("java.lang.Object"); //$NON-NLS-1$
			String identifier= node.getIdentifier();
			boolean isInStaticContext= ASTResolving.isInStaticContext(node);
			
			loop: for (int i= 0; i < varsAndMethodsInScope.length; i++) {
				IBinding varOrMeth= varsAndMethodsInScope[i];
				if (varOrMeth instanceof IVariableBinding) {
					IVariableBinding curr= (IVariableBinding) varOrMeth;
					String currName= curr.getName();
					if (currName.equals(otherNameInAssign)) {
						continue loop;
					}
					boolean isFinal= Modifier.isFinal(curr.getModifiers());
					if (isFinal && curr.isField() && isWriteAccess) {
						continue loop;
					}
					if (isInStaticContext && !Modifier.isStatic(curr.getModifiers())) {
						continue loop;
					}
					
					int relevance= 0;
					if (NameMatcher.isSimilarName(currName, identifier)) {
						relevance += 3; // variable with a similar name than the unresolved variable
					}
					if (currName.equalsIgnoreCase(identifier)) {
						relevance+= 5;
					}
					ITypeBinding varType= curr.getType();
					if (varType != null) {
						if (guessedType != null && guessedType != objectBinding) { // too many result with object
							// var type is compatible with the guessed type
							if (!isWriteAccess && TypeRules.canAssign(varType, guessedType)
									|| isWriteAccess && TypeRules.canAssign(guessedType, varType)) {
								relevance += 2; // unresolved variable can be assign to this variable
							}
						}
						if (methodSenderName != null && hasMethodWithName(varType, methodSenderName)) {
							relevance += 2;
						}
						if (fieldSenderName != null && hasFieldWithName(varType, fieldSenderName)) {
							relevance += 2;
						}
					}
								
					if (relevance > 0) {
						String label= CorrectionMessages.getFormattedString("UnresolvedElementsSubProcessor.changevariable.description", currName); //$NON-NLS-1$
						proposals.add(new RenameNodeCompletionProposal(label, cu, node.getStartPosition(), node.getLength(), currName, relevance));
					}
				} else if (varOrMeth instanceof IMethodBinding) {
					IMethodBinding curr= (IMethodBinding) varOrMeth;
					if (!curr.isConstructor() && guessedType != null && TypeRules.canAssign(curr.getReturnType(), guessedType)) {
						if (NameMatcher.isSimilarName(curr.getName(), identifier)) {
							AST ast= astRoot.getAST();
							ASTRewrite rewrite= ASTRewrite.create(ast);
							String label= CorrectionMessages.getFormattedString("UnresolvedElementsSubProcessor.changetomethod.description", ASTResolving.getMethodSignature(curr, false)); //$NON-NLS-1$
							Image image= JavaPluginImages.get(JavaPluginImages.IMG_CORRECTION_CHANGE);
							LinkedCorrectionProposal proposal= new LinkedCorrectionProposal(label, cu, rewrite, 8, image);
							proposals.add(proposal);
							
							MethodInvocation newInv= ast.newMethodInvocation();
							newInv.setName(ast.newSimpleName(curr.getName()));
							ITypeBinding[] parameterTypes= curr.getParameterTypes();
							for (int k= 0; k < parameterTypes.length; k++) {
								ASTNode arg= ASTNodeFactory.newDefaultExpression(ast, parameterTypes[k]);
								newInv.arguments().add(arg);
								proposal.addLinkedPosition(rewrite.track(arg), false, null);
							}
							rewrite.replace(node, newInv, null);
						}
					}
				}
			}
		}
		if (binding != null && binding.isArray()) {
			String idLength= "length"; //$NON-NLS-1$
			String label= CorrectionMessages.getFormattedString("UnresolvedElementsSubProcessor.changevariable.description", idLength); //$NON-NLS-1$
			proposals.add(new RenameNodeCompletionProposal(label, cu, node.getStartPosition(), node.getLength(), idLength, 8)); //$NON-NLS-1$
		}
	}
	
	private static boolean hasMethodWithName(ITypeBinding typeBinding, String name) {
		IVariableBinding[] fields= typeBinding.getDeclaredFields();
		for (int i= 0; i < fields.length; i++) {
			if (fields[i].getName().equals(name)) {
				return true;
			}
		}
		ITypeBinding superclass= typeBinding.getSuperclass();
		if (superclass != null) {
			return hasMethodWithName(superclass, name);
		}
		return false;
	}
	
	private static boolean hasFieldWithName(ITypeBinding typeBinding, String name) {
		IMethodBinding[] methods= typeBinding.getDeclaredMethods();
		for (int i= 0; i < methods.length; i++) {
			if (methods[i].getName().equals(name)) {
				return true;
			}
		}
		ITypeBinding superclass= typeBinding.getSuperclass();
		if (superclass != null) {
			return hasMethodWithName(superclass, name);
		}
		return false;
	}
	
	private static int evauateTypeKind(ASTNode node, IJavaProject project) {
		int kind= SimilarElementsRequestor.ALL_TYPES;
		
		ASTNode parent= node.getParent();
		while (parent instanceof QualifiedName) {
			if (node.getLocationInParent() == QualifiedName.QUALIFIER_PROPERTY) {
				return SimilarElementsRequestor.REF_TYPES;
			}
			node= parent;
			parent= parent.getParent();
		}
		while (parent instanceof Type) {
			if (parent instanceof QualifiedType) {
				if (node.getLocationInParent() == QualifiedType.QUALIFIER_PROPERTY) {
					return SimilarElementsRequestor.REF_TYPES;
				}
			} else if (parent instanceof ParameterizedType) {
				if (node.getLocationInParent() == ParameterizedType.TYPE_ARGUMENTS_PROPERTY) {
					return SimilarElementsRequestor.REF_TYPES;
				}
			} else if (parent instanceof WildcardType) {
				if (node.getLocationInParent() == WildcardType.BOUND_PROPERTY) {
					return SimilarElementsRequestor.REF_TYPES;
				}
			}
			node= parent;
			parent= parent.getParent();
		}
		
		switch (parent.getNodeType()) {
			case ASTNode.TYPE_DECLARATION:
				if (node.getLocationInParent() == TypeDeclaration.SUPER_INTERFACE_TYPES_PROPERTY) {
					kind= SimilarElementsRequestor.INTERFACES;
				} else if (node.getLocationInParent() == TypeDeclaration.SUPERCLASS_TYPE_PROPERTY) {
					kind= SimilarElementsRequestor.CLASSES;
				}
				break;
			case ASTNode.ENUM_DECLARATION:
				kind= SimilarElementsRequestor.INTERFACES;
				break;
			case ASTNode.METHOD_DECLARATION:
				if (node.getLocationInParent() == MethodDeclaration.THROWN_EXCEPTIONS_PROPERTY) {
					kind= SimilarElementsRequestor.CLASSES;
				} else if (node.getLocationInParent() == MethodDeclaration.RETURN_TYPE2_PROPERTY) {
					kind= SimilarElementsRequestor.ALL_TYPES | SimilarElementsRequestor.VOIDTYPE;
				}
				break;
			case ASTNode.INSTANCEOF_EXPRESSION:
				kind= SimilarElementsRequestor.REF_TYPES  & ~SimilarElementsRequestor.VARIABLES;
				break;
			case ASTNode.THROW_STATEMENT:
				kind= SimilarElementsRequestor.CLASSES;
				break;
			case ASTNode.CLASS_INSTANCE_CREATION:
				if (((ClassInstanceCreation) parent).getAnonymousClassDeclaration() == null) {
					kind= SimilarElementsRequestor.CLASSES;
				} else {
					kind= SimilarElementsRequestor.CLASSES | SimilarElementsRequestor.INTERFACES;
				}
				break;
			case ASTNode.SINGLE_VARIABLE_DECLARATION:
				int superParent= parent.getParent().getNodeType();
				if (superParent == ASTNode.CATCH_CLAUSE) {
					kind= SimilarElementsRequestor.CLASSES;
				}
				break;
			case ASTNode.TAG_ELEMENT:
				kind= SimilarElementsRequestor.REF_TYPES & ~SimilarElementsRequestor.VARIABLES;
				break;
			case ASTNode.MARKER_ANNOTATION:
			case ASTNode.SINGLE_MEMBER_ANNOTATION:
			case ASTNode.NORMAL_ANNOTATION:
				kind= SimilarElementsRequestor.ANNOTATIONS;
				break;
			case ASTNode.TYPE_PARAMETER:
				if (((TypeParameter) parent).typeBounds().indexOf(node) > 0) {
					kind= SimilarElementsRequestor.INTERFACES;
				} else {
					kind= SimilarElementsRequestor.REF_TYPES;
				}
				break;
			default:
		}
		if (!JavaModelUtil.is50OrHigher(project)) {
			return kind & ~(SimilarElementsRequestor.ANNOTATIONS | SimilarElementsRequestor.ENUMS | SimilarElementsRequestor.VARIABLES);
		}
		return kind;
	}
	
	
	public static void getTypeProposals(IInvocationContext context, IProblemLocation problem, Collection proposals) throws CoreException {
		ICompilationUnit cu= context.getCompilationUnit();
		
		ASTNode selectedNode= problem.getCoveringNode(context.getASTRoot());
		if (selectedNode == null) {
			return;
		}

		int kind= evauateTypeKind(selectedNode, cu.getJavaProject());
		
		while (selectedNode.getParent() instanceof QualifiedName) {
			selectedNode= selectedNode.getParent();
		}

		Name node= null;
		if (selectedNode instanceof SimpleType) {
			node= ((SimpleType) selectedNode).getName();
		} else if (selectedNode instanceof ArrayType) {
			Type elementType= ((ArrayType) selectedNode).getElementType();
			if (elementType.isSimpleType()) {
				node= ((SimpleType) elementType).getName();
			}
		} else if (selectedNode instanceof Name) {
			node= (Name) selectedNode;
		} else {
			return;
		}
		
		// change to similar type proposals
		addSimilarTypeProposals(kind, cu, node, 3, proposals);
		
		// add type
		addNewTypeProposals(cu, node, kind, 0, proposals);
	}

	private static void addSimilarTypeProposals(int kind, ICompilationUnit cu, Name node, int relevance, Collection proposals) throws CoreException {
		SimilarElement[] elements= SimilarElementsRequestor.findSimilarElement(cu, node, kind);
		
		// try to resolve type in context -> highest severity
		String resolvedTypeName= null;
		ITypeBinding binding= ASTResolving.guessBindingForTypeReference(node);
		if (binding != null) {
			if (binding.isArray()) {
				binding= binding.getElementType();
			}
			resolvedTypeName= binding.getQualifiedName();
			proposals.add(createTypeRefChangeProposal(cu, resolvedTypeName, node, relevance + 2));
		}
		// add all similar elements
		for (int i= 0; i < elements.length; i++) {
			SimilarElement elem= elements[i];
			if ((elem.getKind() & SimilarElementsRequestor.ALL_TYPES) != 0) {
				String fullName= elem.getName();
				if (!fullName.equals(resolvedTypeName)) {
					proposals.add(createTypeRefChangeProposal(cu, fullName, node, relevance));
				}
			}
		}
	}

	private static CUCorrectionProposal createTypeRefChangeProposal(ICompilationUnit cu, String fullName, Name node, int relevance) throws CoreException {
		ImportRewrite importRewrite= new ImportRewrite(cu);
		importRewrite.setFindAmbiguosImports(true);
		String simpleName= importRewrite.addImport(fullName);
		String packName= Signature.getQualifier(fullName);		
		String[] arg= { simpleName, packName };
		if (!isLikelyTypeName(simpleName)) {
			relevance -= 2;
		}
		
		CUCorrectionProposal proposal;
		if (node.isSimpleName() && simpleName.equals(((SimpleName) node).getIdentifier())) { // import only
			// import only
			String label= CorrectionMessages.getFormattedString("UnresolvedElementsSubProcessor.importtype.description", arg); //$NON-NLS-1$
			Image image= JavaPluginImages.get(JavaPluginImages.IMG_OBJS_IMPDECL);
			proposal= new CUCorrectionProposal(label, cu, relevance + 100, image);
		} else {
			String label;
			if (packName.length() == 0) {
				label= CorrectionMessages.getFormattedString("UnresolvedElementsSubProcessor.changetype.nopack.description", simpleName); //$NON-NLS-1$
			} else {
				label= CorrectionMessages.getFormattedString("UnresolvedElementsSubProcessor.changetype.description", arg); //$NON-NLS-1$
			}
			proposal= new RenameNodeCompletionProposal(label, cu, node.getStartPosition(), node.getLength(), simpleName, relevance); //$NON-NLS-1$
		}
		proposal.setImportRewrite(importRewrite);
		return proposal;
	}
	
	private static boolean isLikelyTypeName(String name) {
		return name.length() > 0 && Character.isUpperCase(name.charAt(0));
	}
	
	private static boolean isLikelyPackageName(String name) {
		if (name.length() != 0) {
			int i= 0;
			do {
				if (Character.isUpperCase(name.charAt(i))) {
					return false;
				}
				i= name.indexOf('.', i) + 1;
			} while (i != 0 && i < name.length());
		}
		return true;
	}
	
	private static boolean isLikelyTypeParameterName(String name) {
		return name.length() == 1 && Character.isUpperCase(name.charAt(0));
	}

	private static void addNewTypeProposals(ICompilationUnit cu, Name refNode, int kind, int relevance, Collection proposals) throws JavaModelException {
		Name node= refNode;
		do {
			String typeName= ASTNodes.getSimpleNameIdentifier(node);
			Name qualifier= null;
			// only propose to create types for qualifiers when the name starts with upper case
			boolean isPossibleName= isLikelyTypeName(typeName) || (node == refNode);
			if (isPossibleName) {
				IPackageFragment enclosingPackage= null;
				IType enclosingType= null;
				if (node.isSimpleName()) {
					enclosingPackage= (IPackageFragment) cu.getParent();
					// don't suggest member type, user can select it in wizard
				} else {
					Name qualifierName= ((QualifiedName) node).getQualifier();
					 IBinding binding= qualifierName.resolveBinding(); 
					 if (binding instanceof ITypeBinding) {
						enclosingType=(IType) binding.getJavaElement();
					 } else if (binding instanceof IPackageBinding) {
						qualifier= qualifierName;
					 	enclosingPackage= (IPackageFragment) binding.getJavaElement();
					 } else {
					 	IJavaElement[] res= cu.codeSelect(qualifierName.getStartPosition(), qualifierName.getLength());
						if (res!= null && res.length > 0 && res[0] instanceof IType) {
							enclosingType= (IType) res[0];
						} else {
							qualifier= qualifierName;
							enclosingPackage= JavaModelUtil.getPackageFragmentRoot(cu).getPackageFragment(ASTResolving.getFullName(qualifierName));
						}
					 }
				}
				int rel= relevance;
				if (enclosingPackage != null && isLikelyPackageName(enclosingPackage.getElementName())) {
					rel += 3;
				}
				
				if ((enclosingPackage != null && !enclosingPackage.getCompilationUnit(typeName + ".java").exists()) // new top level type //$NON-NLS-1$
						|| (enclosingType != null && !enclosingType.isReadOnly() && !enclosingType.getType(typeName).exists())) { // new member type
					IJavaElement enclosing= enclosingPackage != null ? (IJavaElement) enclosingPackage : enclosingType;
					
					if ((kind & SimilarElementsRequestor.CLASSES) != 0) {
			            proposals.add(new NewCUCompletionUsingWizardProposal(cu, node, NewCUCompletionUsingWizardProposal.K_CLASS, enclosing, rel+2));
					}
					if ((kind & SimilarElementsRequestor.INTERFACES) != 0) {			
			            proposals.add(new NewCUCompletionUsingWizardProposal(cu, node, NewCUCompletionUsingWizardProposal.K_INTERFACE, enclosing, rel+1));
					}
					if ((kind & SimilarElementsRequestor.ENUMS) != 0) {
			            proposals.add(new NewCUCompletionUsingWizardProposal(cu, node, NewCUCompletionUsingWizardProposal.K_ENUM, enclosing, rel));
					}
					if (kind == SimilarElementsRequestor.ANNOTATIONS) { // only when in annotation
			            proposals.add(new NewCUCompletionUsingWizardProposal(cu, node, NewCUCompletionUsingWizardProposal.K_ANNOTATION, enclosing, rel+4));
					}		
				}		
			}
			node= qualifier;
		} while (node != null);
		
		// type parameter proposals
		if (refNode.isSimpleName() && ((kind & SimilarElementsRequestor.VARIABLES)  != 0)) {
			CompilationUnit root= (CompilationUnit) refNode.getRoot();
			String name= ((SimpleName) refNode).getIdentifier();
			BodyDeclaration declaration= ASTResolving.findParentBodyDeclaration(refNode);
			int baseRel= relevance;
			if (isLikelyTypeParameterName(name)) {
				baseRel += 4;
			}
			while (declaration != null) {
				IBinding binding= null;
				int rel= baseRel;
				if (declaration instanceof MethodDeclaration) {
					binding= ((MethodDeclaration) declaration).resolveBinding();
				} else if (declaration instanceof TypeDeclaration) {
					binding= ((TypeDeclaration) declaration).resolveBinding();
					rel++;
				}
				if (binding != null) {
					AddTypeParameterProposal proposal= new AddTypeParameterProposal(cu, binding, root, name, null, rel);
					proposals.add(proposal);
				}
				declaration= ASTResolving.findParentBodyDeclaration(declaration.getParent());
			}
		}
	}
	
	public static void getMethodProposals(IInvocationContext context, IProblemLocation problem, boolean isOnlyParameterMismatch, Collection proposals) throws CoreException {

		ICompilationUnit cu= context.getCompilationUnit();
		
		CompilationUnit astRoot= context.getASTRoot();
		ASTNode selectedNode= problem.getCoveringNode(astRoot);
		
		if (!(selectedNode instanceof SimpleName)) {
			return;
		}
		SimpleName nameNode= (SimpleName) selectedNode;

		List arguments;
		Expression sender;
		boolean isSuperInvocation;
		
		ASTNode invocationNode= nameNode.getParent();
		if (invocationNode instanceof MethodInvocation) {
			MethodInvocation methodImpl= (MethodInvocation) invocationNode;
			arguments= methodImpl.arguments();
			sender= methodImpl.getExpression();
			isSuperInvocation= false;
		} else if (invocationNode instanceof SuperMethodInvocation) {
			SuperMethodInvocation methodImpl= (SuperMethodInvocation) invocationNode;
			arguments= methodImpl.arguments();
			sender= methodImpl.getQualifier();
			isSuperInvocation= true;
		} else {
			return;
		}
		
		String methodName= nameNode.getIdentifier();
		int nArguments= arguments.size();
			
		// corrections
		IBinding[] bindings= (new ScopeAnalyzer(astRoot)).getDeclarationsInScope(nameNode, ScopeAnalyzer.METHODS);
		
		HashSet suggestedRenames= new HashSet();
		for (int i= 0; i < bindings.length; i++) {
			IMethodBinding binding= (IMethodBinding) bindings[i];
			String curr= binding.getName();
			if (!curr.equals(methodName) && binding.getParameterTypes().length == nArguments && NameMatcher.isSimilarName(methodName, curr) && suggestedRenames.add(curr)) {
				String label= CorrectionMessages.getFormattedString("UnresolvedElementsSubProcessor.changemethod.description", curr); //$NON-NLS-1$
				proposals.add(new RenameNodeCompletionProposal(label, context.getCompilationUnit(), problem.getOffset(), problem.getLength(), curr, 6));
			}
		}
		suggestedRenames= null;
		
		if (isOnlyParameterMismatch) {
			ArrayList parameterMismatchs= new ArrayList();
			for (int i= 0; i < bindings.length; i++) {
				IMethodBinding binding= (IMethodBinding) bindings[i];
				if (binding.getName().equals(methodName)) {
					parameterMismatchs.add(binding);
				}
			}
			addParameterMissmatchProposals(context, problem, parameterMismatchs, invocationNode, arguments, proposals);
		}
		
		// new method
		addNewMethodProposals(cu, astRoot, sender, arguments, isSuperInvocation, invocationNode, methodName, proposals);
		
		if (!isOnlyParameterMismatch && !isSuperInvocation && sender != null) {
			addMissingCastParentsProposal(cu, (MethodInvocation) invocationNode, proposals);
		}
		
		if (!isSuperInvocation && sender == null && invocationNode.getParent() instanceof ThrowStatement) {
			String str= "new ";   //$NON-NLS-1$ // do it the manual way, copting all the arguments is nasty
			String label= CorrectionMessages.getString("UnresolvedElementsSubProcessor.addnewkeyword.description"); //$NON-NLS-1$
			int relevance= Character.isUpperCase(methodName.charAt(0)) ? 7 : 4;
			ReplaceCorrectionProposal proposal= new ReplaceCorrectionProposal(label, cu, invocationNode.getStartPosition(), 0, str, relevance);
			proposals.add(proposal);
		}

	}

	private static void addNewMethodProposals(ICompilationUnit cu, CompilationUnit astRoot, Expression sender, List arguments, boolean isSuperInvocation, ASTNode invocationNode, String methodName, Collection proposals) throws JavaModelException {
		ITypeBinding binding= null;
		if (sender != null) {
			binding= sender.resolveTypeBinding();
		} else {
			binding= Bindings.getBindingOfParentType(invocationNode);
			if (isSuperInvocation && binding != null) {
				binding= binding.getSuperclass();
			}				
		}
		if (binding != null && binding.isFromSource()) {
			ITypeBinding senderDeclBinding= binding.getTypeDeclaration();
			
			ICompilationUnit targetCU= ASTResolving.findCompilationUnitForBinding(cu, astRoot, senderDeclBinding);
			if (targetCU != null) {			
				String label;
				Image image;
				ITypeBinding[] parameterTypes= getParameterTypes(arguments);
				String sig= ASTResolving.getMethodSignature(methodName, parameterTypes);
				
				if (ASTResolving.isUseableTypeInContext(parameterTypes, senderDeclBinding, false)) {
					if (cu.equals(targetCU)) {
						label= CorrectionMessages.getFormattedString("UnresolvedElementsSubProcessor.createmethod.description", sig); //$NON-NLS-1$
						image= JavaPluginImages.get(JavaPluginImages.IMG_MISC_PRIVATE);
					} else {
						label= CorrectionMessages.getFormattedString("UnresolvedElementsSubProcessor.createmethod.other.description", new Object[] { sig, targetCU.getElementName() } ); //$NON-NLS-1$
						image= JavaPluginImages.get(JavaPluginImages.IMG_MISC_PUBLIC);
					}
					proposals.add(new NewMethodCompletionProposal(label, targetCU, invocationNode, arguments, senderDeclBinding, 5, image));
				}
				if (senderDeclBinding.isNested() && cu.equals(targetCU) && sender == null && Bindings.findMethodInHierarchy(senderDeclBinding, methodName, (ITypeBinding[]) null) == null) { // no covering method
					ASTNode anonymDecl= astRoot.findDeclaringNode(senderDeclBinding);
					if (anonymDecl != null) {
						senderDeclBinding= Bindings.getBindingOfParentType(anonymDecl.getParent());
						if (!senderDeclBinding.isAnonymous() && ASTResolving.isUseableTypeInContext(parameterTypes, senderDeclBinding, false)) {
							String[] args= new String[] { sig, ASTResolving.getTypeSignature(senderDeclBinding) };
							label= CorrectionMessages.getFormattedString("UnresolvedElementsSubProcessor.createmethod.other.description", args); //$NON-NLS-1$
							image= JavaPluginImages.get(JavaPluginImages.IMG_MISC_PROTECTED);
							proposals.add(new NewMethodCompletionProposal(label, targetCU, invocationNode, arguments, senderDeclBinding, 5, image));
						}
					}
				}
			}
		}
	}

	private static void addMissingCastParentsProposal(ICompilationUnit cu, MethodInvocation invocationNode, Collection proposals) {
		Expression sender= invocationNode.getExpression();
		if (sender instanceof ThisExpression) {
			return;
		}
		
		ITypeBinding senderBinding= sender.resolveTypeBinding();
		if (senderBinding == null || Modifier.isFinal(senderBinding.getModifiers())) {
			return;
		}
		
		if (sender instanceof Name && ((Name) sender).resolveBinding() instanceof ITypeBinding) {
			return; // static access
		}
		
		ASTNode parent= invocationNode.getParent();
		while (parent instanceof Expression && parent.getNodeType() != ASTNode.CAST_EXPRESSION) {
			parent= parent.getParent();
		}
		boolean hasCastProposal= false;
		if (parent instanceof CastExpression) {
			//	(TestCase) x.getName() -> ((TestCase) x).getName
			hasCastProposal= useExistingParentCastProposal(cu, (CastExpression) parent, sender, invocationNode.getName(), getArgumentTypes(invocationNode.arguments()), proposals);
		}
		if (!hasCastProposal) {
			// x.getName() -> ((TestCase) x).getName
			
			Expression target= sender;
			while (target instanceof ParenthesizedExpression) {
				target= ((ParenthesizedExpression) target).getExpression();
			}

			String label;
			if (target.getNodeType() != ASTNode.CAST_EXPRESSION) {
				String targetName= null;
				if (target.getLength() <= 18) {
					targetName= ASTNodes.asString(target);
				}
				if (targetName == null) {
					label= CorrectionMessages.getString("UnresolvedElementsSubProcessor.methodtargetcast.description"); //$NON-NLS-1$
				} else {
					label= CorrectionMessages.getFormattedString("UnresolvedElementsSubProcessor.methodtargetcast2.description", targetName); //$NON-NLS-1$
				}
			} else {
				String targetName= null;
				if (target.getLength() <= 18) {
					targetName= ASTNodes.asString(((CastExpression)target).getExpression());
				}
				if (targetName == null) {
					label= CorrectionMessages.getString("UnresolvedElementsSubProcessor.changemethodtargetcast.description"); //$NON-NLS-1$
				} else {
					label= CorrectionMessages.getFormattedString("UnresolvedElementsSubProcessor.changemethodtargetcast2.description", targetName); //$NON-NLS-1$
				}
			}
			proposals.add(new CastCompletionProposal(label, cu, target, (ITypeBinding) null, 3));
		}
	}

	private static boolean useExistingParentCastProposal(ICompilationUnit cu, CastExpression expression, Expression accessExpression, SimpleName accessSelector, ITypeBinding[] paramTypes, Collection proposals) {
		ITypeBinding castType= expression.getType().resolveBinding();
		if (castType == null) {
			return false;
		}
		if (paramTypes != null) {
			if (Bindings.findMethodInHierarchy(castType, accessSelector.getIdentifier(), paramTypes) == null) {
				return false;
			}
		} else if (Bindings.findFieldInHierarchy(castType, accessSelector.getIdentifier()) == null) {
			return false;
		}
		ITypeBinding bindingToCast= accessExpression.resolveTypeBinding();
		if (bindingToCast != null && !TypeRules.canCast(castType, bindingToCast)) {
			return false;
		}
		
		IMethodBinding res= Bindings.findMethodInHierarchy(castType, accessSelector.getIdentifier(), paramTypes);
		if (res != null) {
			AST ast= expression.getAST();
			ASTRewrite rewrite= ASTRewrite.create(ast);
			CastExpression newCast= ast.newCastExpression();
			newCast.setType((Type) ASTNode.copySubtree(ast, expression.getType()));
			newCast.setExpression((Expression) rewrite.createCopyTarget(accessExpression));
			ParenthesizedExpression parents= ast.newParenthesizedExpression();
			parents.setExpression(newCast);
			
			ASTNode node= rewrite.createCopyTarget(expression.getExpression());
			rewrite.replace(expression, node, null);
			rewrite.replace(accessExpression, parents, null);

			String label= CorrectionMessages.getString("UnresolvedElementsSubProcessor.missingcastbrackets.description"); //$NON-NLS-1$
			Image image= JavaPluginImages.get(JavaPluginImages.IMG_CORRECTION_CAST);
			ASTRewriteCorrectionProposal proposal= new ASTRewriteCorrectionProposal(label, cu, rewrite, 8, image);
			proposals.add(proposal);
			return true;
		}
		return false;
	}

	private static void addParameterMissmatchProposals(IInvocationContext context, IProblemLocation problem, List similarElements, ASTNode invocationNode, List arguments, Collection proposals) throws CoreException {
		int nSimilarElements= similarElements.size();
		ITypeBinding[] argTypes= getArgumentTypes(arguments);
		if (argTypes == null || nSimilarElements == 0)  {
			return;
		}

		for (int i= 0; i < nSimilarElements; i++) {
			IMethodBinding elem = (IMethodBinding) similarElements.get(i);
			int diff= elem.getParameterTypes().length - argTypes.length;
			if (diff == 0) {
				int nProposals= proposals.size();
				doEqualNumberOfParameters(context, invocationNode, problem, arguments, argTypes, elem, proposals);
				if (nProposals != proposals.size()) {
					return; // only suggest for one method (avoid duplicated proposals)
				}
			} else if (diff > 0) {
				doMoreParameters(context, problem, invocationNode, arguments, argTypes, elem, proposals);
			} else {
				doMoreArguments(context, problem, invocationNode, arguments, argTypes, elem, proposals);
			}
		}
	}
	
	private static void doMoreParameters(IInvocationContext context, IProblemLocation problem, ASTNode invocationNode, List arguments, ITypeBinding[] argTypes, IMethodBinding methodBinding, Collection proposals) throws CoreException {
		ITypeBinding[] paramTypes= methodBinding.getParameterTypes();
		int k= 0, nSkipped= 0;
		int diff= paramTypes.length - argTypes.length;
		int[] indexSkipped= new int[diff];
		for (int i= 0; i < paramTypes.length; i++) {
			if (k < argTypes.length && TypeRules.canAssign(argTypes[k], paramTypes[i])) {
				k++; // match
			} else {
				if (nSkipped >= diff) {
					return; // too different
				} 
				indexSkipped[nSkipped++]= i;
			}
		}
		ITypeBinding declaringType= methodBinding.getDeclaringClass();
		ICompilationUnit cu= context.getCompilationUnit();
		CompilationUnit astRoot= context.getASTRoot();
			
		// add arguments
		{			
			String[] arg= new String[] { ASTResolving.getMethodSignature(methodBinding, false) };
			String label;
			if (diff == 1) {
				label= CorrectionMessages.getFormattedString("UnresolvedElementsSubProcessor.addargument.description", arg); //$NON-NLS-1$
			} else {
				label= CorrectionMessages.getFormattedString("UnresolvedElementsSubProcessor.addarguments.description", arg); //$NON-NLS-1$
			}			
			AddArgumentCorrectionProposal proposal= new AddArgumentCorrectionProposal(label, context.getCompilationUnit(), invocationNode, indexSkipped, paramTypes, 8);
			proposal.setImage(JavaPluginImages.get(JavaPluginImages.IMG_CORRECTION_ADD));
			proposals.add(proposal);				
		}
		
		// remove parameters
		if (!declaringType.isFromSource()) {
			return;
		}
		
		ICompilationUnit targetCU= ASTResolving.findCompilationUnitForBinding(cu, astRoot, declaringType);
		if (targetCU != null) {
			IMethodBinding methodDecl= methodBinding.getMethodDeclaration();
			ITypeBinding[] declParameterTypes= methodDecl.getParameterTypes();
			
			ChangeDescription[] changeDesc= new ChangeDescription[declParameterTypes.length];
			ITypeBinding[] changedTypes= new ITypeBinding[diff];
			for (int i= diff - 1; i >= 0; i--) {
				int idx= indexSkipped[i];
				changeDesc[idx]= new RemoveDescription();
				changedTypes[i]= declParameterTypes[idx];
			}
			String[] arg= new String[] { ASTResolving.getMethodSignature(methodDecl, !cu.equals(targetCU)), getTypeNames(changedTypes) };
			String label;
			if (methodDecl.isConstructor()) {
				if (diff == 1) {
					label= CorrectionMessages.getFormattedString("UnresolvedElementsSubProcessor.removeparam.constr.description", arg); //$NON-NLS-1$
				} else {
					label= CorrectionMessages.getFormattedString("UnresolvedElementsSubProcessor.removeparams.constr.description", arg); //$NON-NLS-1$
				}
			} else {
				if (diff == 1) {
					label= CorrectionMessages.getFormattedString("UnresolvedElementsSubProcessor.removeparam.description", arg); //$NON-NLS-1$
				} else {
					label= CorrectionMessages.getFormattedString("UnresolvedElementsSubProcessor.removeparams.description", arg); //$NON-NLS-1$
				}					
			}
		
			Image image= JavaPluginImages.get(JavaPluginImages.IMG_CORRECTION_REMOVE);
			ChangeMethodSignatureProposal proposal= new ChangeMethodSignatureProposal(label, targetCU, invocationNode, methodDecl, changeDesc, null, 5, image);
			proposals.add(proposal);
		}
	}
	
	private static String getTypeNames(ITypeBinding[] types) {
		StringBuffer buf= new StringBuffer();
		for (int i= 0; i < types.length; i++) {
			if (i > 0) {
				buf.append(", "); //$NON-NLS-1$
			}
			buf.append(ASTResolving.getTypeSignature(types[i]));
		}
		return buf.toString();
	}
	
	private static String getArgumentName(ICompilationUnit cu, List arguments, int index) {
		String def= String.valueOf(index + 1);
		
		ASTNode expr= (ASTNode) arguments.get(index);
		if (expr.getLength() > 18) {
			return def;
		}
		ASTMatcher matcher= new ASTMatcher();
		for (int i= 0; i < arguments.size(); i++) {
			if (i != index && matcher.safeSubtreeMatch(expr, arguments.get(i))) {
				return def;
			}
		}
		return '\'' + ASTNodes.asString(expr) + '\'';
	}

	private static void doMoreArguments(IInvocationContext context, IProblemLocation problem, ASTNode invocationNode, List arguments, ITypeBinding[] argTypes, IMethodBinding methodRef, Collection proposals) throws CoreException {
		ITypeBinding[] paramTypes= methodRef.getParameterTypes();
		int k= 0, nSkipped= 0;
		int diff= argTypes.length - paramTypes.length;
		int[] indexSkipped= new int[diff];
		for (int i= 0; i < argTypes.length; i++) {
			if (k < paramTypes.length && TypeRules.canAssign(argTypes[i], paramTypes[k])) {
				k++; // match
			} else {
				if (nSkipped >= diff) {
					return; // too different
				} 
				indexSkipped[nSkipped++]= i;
			}
		}

		ICompilationUnit cu= context.getCompilationUnit();
		CompilationUnit astRoot= context.getASTRoot();
	
		// remove arguments
		{
			ASTRewrite rewrite= ASTRewrite.create(astRoot.getAST());
			
			for (int i= diff - 1; i >= 0; i--) {
				rewrite.remove((Expression) arguments.get(indexSkipped[i]), null);
			}
			String[] arg= new String[] { ASTResolving.getMethodSignature(methodRef, false) };
			String label;
			if (diff == 1) {
				label= CorrectionMessages.getFormattedString("UnresolvedElementsSubProcessor.removeargument.description", arg); //$NON-NLS-1$
			} else {
				label= CorrectionMessages.getFormattedString("UnresolvedElementsSubProcessor.removearguments.description", arg); //$NON-NLS-1$
			}			
			Image image= JavaPluginImages.get(JavaPluginImages.IMG_CORRECTION_REMOVE);
			ASTRewriteCorrectionProposal proposal= new ASTRewriteCorrectionProposal(label, cu, rewrite, 8, image);
			proposals.add(proposal);				
		}
		
		IMethodBinding methodDecl= methodRef.getMethodDeclaration();
		ITypeBinding declaringType= methodDecl.getDeclaringClass();
		
		// add parameters
		if (!declaringType.isFromSource()) {
			return;
		}
		ICompilationUnit targetCU= ASTResolving.findCompilationUnitForBinding(cu, astRoot, declaringType);
		if (targetCU != null) {
			boolean isDifferentCU= !cu.equals(targetCU);
			
			if (isImplicitConstructor(methodDecl, targetCU)) {
				return;
			}
			
			ChangeDescription[] changeDesc= new ChangeDescription[argTypes.length];
			ITypeBinding[] changeTypes= new ITypeBinding[diff];
			for (int i= diff - 1; i >= 0; i--) {
				int idx= indexSkipped[i];
				Expression arg= (Expression) arguments.get(idx);
				String name= arg instanceof SimpleName ? ((SimpleName) arg).getIdentifier() : null;
				ITypeBinding newType= Bindings.normalizeTypeBinding(argTypes[idx]);
				if (newType == null) {
					newType= astRoot.getAST().resolveWellKnownType("java.lang.Object"); //$NON-NLS-1$
				}
				if (!ASTResolving.isUseableTypeInContext(newType, methodDecl, false)) {
					return;
				}
				changeDesc[idx]= new InsertDescription(newType, name);
				changeTypes[i]= newType;
			}
			String[] arg= new String[] { ASTResolving.getMethodSignature(methodDecl, isDifferentCU), getTypeNames(changeTypes) };
			String label;
			if (methodDecl.isConstructor()) {
				if (diff == 1) {
					label= CorrectionMessages.getFormattedString("UnresolvedElementsSubProcessor.addparam.constr.description", arg); //$NON-NLS-1$
				} else {
					label= CorrectionMessages.getFormattedString("UnresolvedElementsSubProcessor.addparams.constr.description", arg); //$NON-NLS-1$
				}
			} else {
				if (diff == 1) {
					label= CorrectionMessages.getFormattedString("UnresolvedElementsSubProcessor.addparam.description", arg); //$NON-NLS-1$
				} else {
					label= CorrectionMessages.getFormattedString("UnresolvedElementsSubProcessor.addparams.description", arg); //$NON-NLS-1$
				}
			}	
			Image image= JavaPluginImages.get(JavaPluginImages.IMG_CORRECTION_ADD);
			ChangeMethodSignatureProposal proposal= new ChangeMethodSignatureProposal(label, targetCU, invocationNode, methodDecl, changeDesc, null, 5, image);
			proposals.add(proposal);
		}
	}
	
	private static boolean isImplicitConstructor(IMethodBinding meth, ICompilationUnit targetCU) {
		if (meth.isConstructor() && meth.getParameterTypes().length == 0) {
			IMethodBinding[] bindings= meth.getDeclaringClass().getDeclaredMethods();
			// implicit constructors must be the only constructor
			for (int i= 0; i < bindings.length; i++) {
				IMethodBinding curr= bindings[i];
				if (curr.isConstructor() && curr != meth) {
					return false;
				}
			}
			ASTParser parser= ASTParser.newParser(ASTProvider.AST_LEVEL);
			parser.setSource(targetCU);
			parser.setFocalPosition(0);
			parser.setResolveBindings(true);
			CompilationUnit unit= (CompilationUnit) parser.createAST(null);
			return unit.findDeclaringNode(meth.getKey()) == null;
		}
		return false;		
	}
	
	
	
	private static ITypeBinding[] getParameterTypes(List args) {
		ITypeBinding[] params= new ITypeBinding[args.size()];
		for (int i= 0; i < args.size(); i++) {
			Expression expr= (Expression) args.get(i);
			ITypeBinding curr= Bindings.normalizeTypeBinding(expr.resolveTypeBinding());
			if (curr == null) {
				curr= expr.getAST().resolveWellKnownType("java.lang.Object"); //$NON-NLS-1$
			}
			params[i]= curr;
		}
		return params;
	}
	

	
	private static void doEqualNumberOfParameters(IInvocationContext context, ASTNode invocationNode, IProblemLocation problem, List arguments, ITypeBinding[] argTypes, IMethodBinding methodBinding, Collection proposals) throws CoreException {
		ITypeBinding[] paramTypes= methodBinding.getParameterTypes();
		int[] indexOfDiff= new int[paramTypes.length];
		int nDiffs= 0;
		for (int n= 0; n < argTypes.length; n++) {
			if (!TypeRules.canAssign(argTypes[n], paramTypes[n])) {
				indexOfDiff[nDiffs++]= n;
			}
		}
		ITypeBinding declaringTypeDecl= methodBinding.getDeclaringClass().getTypeDeclaration();
		
		ICompilationUnit cu= context.getCompilationUnit();
		CompilationUnit astRoot= context.getASTRoot();
		
		ASTNode nameNode= problem.getCoveringNode(astRoot);
		if (nameNode == null) {
			return;
		}
		
		if (nDiffs == 0) {
			if (nameNode.getParent() instanceof MethodInvocation) {
				MethodInvocation inv= (MethodInvocation) nameNode.getParent();
				if (inv.getExpression() == null) {
					addQualifierToOuterProposal(context, inv, methodBinding, proposals);
				}
			}
			return;
		}
		
		if (nDiffs == 1) { // one argument mismatching: try to fix
			int idx= indexOfDiff[0];
			Expression nodeToCast= (Expression) arguments.get(idx);
			ITypeBinding castType= paramTypes[idx];
			ITypeBinding binding= nodeToCast.resolveTypeBinding();
			if (binding == null || TypeRules.canCast(castType, binding)) {
				String castTypeName= castType.getQualifiedName();
				ASTRewriteCorrectionProposal proposal= TypeMismatchSubProcessor.createCastProposal(context, castTypeName, castType, nodeToCast, 6);
				String[] arg= new String[] { getArgumentName(cu, arguments, idx), castTypeName};
				proposal.setDisplayName(CorrectionMessages.getFormattedString("UnresolvedElementsSubProcessor.addargumentcast.description", arg)); //$NON-NLS-1$
				proposals.add(proposal);
			}
			TypeMismatchSubProcessor.addChangeSenderTypeProposals(context, nodeToCast, castType, false, 5, proposals);
		}
		if (nDiffs == 2) { // try to swap
			int idx1= indexOfDiff[0];
			int idx2= indexOfDiff[1];
			boolean canSwap= TypeRules.canAssign(argTypes[idx1], paramTypes[idx2]) && TypeRules.canAssign(argTypes[idx2], paramTypes[idx1]);
			 if (canSwap) {
				Expression arg1= (Expression) arguments.get(idx1);
				Expression arg2= (Expression) arguments.get(idx2);
				
				ASTRewrite rewrite= ASTRewrite.create(astRoot.getAST());
				rewrite.replace(arg1, rewrite.createCopyTarget(arg2), null);
				rewrite.replace(arg2, rewrite.createCopyTarget(arg1), null);
				{
					String[] arg= new String[] { getArgumentName(cu, arguments, idx1), getArgumentName(cu, arguments, idx2) };
					String label= CorrectionMessages.getFormattedString("UnresolvedElementsSubProcessor.swaparguments.description", arg); //$NON-NLS-1$
					Image image= JavaPluginImages.get(JavaPluginImages.IMG_CORRECTION_CHANGE);
					ASTRewriteCorrectionProposal proposal= new ASTRewriteCorrectionProposal(label, context.getCompilationUnit(), rewrite, 8, image);
					proposals.add(proposal);					
				}
				
				if (declaringTypeDecl.isFromSource()) {
					ICompilationUnit targetCU= ASTResolving.findCompilationUnitForBinding(cu, astRoot, declaringTypeDecl);
					if (targetCU != null) {
						ChangeDescription[] changeDesc= new ChangeDescription[paramTypes.length];
						for (int i= 0; i < nDiffs; i++) {
							changeDesc[idx1]= new SwapDescription(idx2);
						}
						IMethodBinding methodDecl= methodBinding.getMethodDeclaration();
						ITypeBinding[] declParamTypes= methodDecl.getParameterTypes();
						
						ITypeBinding[] swappedTypes= new ITypeBinding[] { declParamTypes[idx1], declParamTypes[idx2] };
						String[] args=  new String[] { ASTResolving.getMethodSignature(methodDecl, !targetCU.equals(cu)), getTypeNames(swappedTypes) };
						String label;
						if (methodDecl.isConstructor()) {
							label= CorrectionMessages.getFormattedString("UnresolvedElementsSubProcessor.swapparams.constr.description", args); //$NON-NLS-1$
						} else {
							label= CorrectionMessages.getFormattedString("UnresolvedElementsSubProcessor.swapparams.description", args); //$NON-NLS-1$
						}
						Image image= JavaPluginImages.get(JavaPluginImages.IMG_CORRECTION_CHANGE);
						ChangeMethodSignatureProposal proposal= new ChangeMethodSignatureProposal(label, targetCU, invocationNode, methodDecl, changeDesc, null, 5, image);
						proposals.add(proposal);
					}
				}
				return;
			}
		}
		
		if (declaringTypeDecl.isFromSource()) {
			ICompilationUnit targetCU= ASTResolving.findCompilationUnitForBinding(cu, astRoot, declaringTypeDecl);
			if (targetCU != null) {
				ChangeDescription[] changeDesc= new ChangeDescription[paramTypes.length];
				for (int i= 0; i < nDiffs; i++) {
					int diffIndex= indexOfDiff[i];
					Expression arg= (Expression) arguments.get(diffIndex);
					String name= arg instanceof SimpleName ? ((SimpleName) arg).getIdentifier() : null;					
					changeDesc[diffIndex]= new EditDescription(argTypes[diffIndex], name);
				}
				IMethodBinding methodDecl= methodBinding.getMethodDeclaration();
				ITypeBinding[] declParamTypes= methodDecl.getParameterTypes();
				
				ITypeBinding[] newParamTypes= new ITypeBinding[changeDesc.length];
				for (int i= 0; i < newParamTypes.length; i++) {
					newParamTypes[i]= changeDesc[i] == null ? declParamTypes[i] : ((EditDescription) changeDesc[i]).type;
				}
				
				String[] args=  new String[] { ASTResolving.getMethodSignature(methodDecl, !targetCU.equals(cu)), ASTResolving.getMethodSignature(methodDecl.getName(), newParamTypes) };
				String label;
				if (methodDecl.isConstructor()) {
					label= CorrectionMessages.getFormattedString("UnresolvedElementsSubProcessor.changeparamsignature.constr.description", args); //$NON-NLS-1$
				} else {
					label= CorrectionMessages.getFormattedString("UnresolvedElementsSubProcessor.changeparamsignature.description", args); //$NON-NLS-1$
				}
				Image image= JavaPluginImages.get(JavaPluginImages.IMG_CORRECTION_CHANGE);
				ChangeMethodSignatureProposal proposal= new ChangeMethodSignatureProposal(label, targetCU, invocationNode, methodDecl, changeDesc, null, 7, image);
				proposals.add(proposal);
			}
		}
	}	
	
	private static ITypeBinding[] getArgumentTypes(List arguments) {
		ITypeBinding[] res= new ITypeBinding[arguments.size()];
		for (int i= 0; i < res.length; i++) {
			Expression expression= (Expression) arguments.get(i);
 			ITypeBinding curr= expression.resolveTypeBinding();
			if (curr == null) {
				return null;
			}
			if (!curr.isNullType()) {	// don't normalize null type
				curr= Bindings.normalizeTypeBinding(curr);
				if (curr == null) {
					curr= expression.getAST().resolveWellKnownType("java.lang.Object"); //$NON-NLS-1$
				}
			}
			res[i]= curr;
		}
		return res;
	}
	
	private static void addQualifierToOuterProposal(IInvocationContext context, MethodInvocation invocationNode, IMethodBinding binding, Collection proposals) throws CoreException {
		ITypeBinding declaringType= binding.getDeclaringClass();
		ITypeBinding parentType= Bindings.getBindingOfParentType(invocationNode);
		ITypeBinding currType= parentType;
		
		boolean isInstanceMethod= !Modifier.isStatic(binding.getModifiers());
		
		while (currType != null && !Bindings.isSuperType(declaringType, currType)) {
			if (isInstanceMethod && Modifier.isStatic(currType.getModifiers())) {
				return;
			}
			currType= currType.getDeclaringClass();
		}
		if (currType == null || currType == parentType) {
			return;
		}
		
		ASTRewrite rewrite= ASTRewrite.create(invocationNode.getAST());
		ImportRewrite imports= new ImportRewrite(context.getCompilationUnit());
		AST ast= invocationNode.getAST();
		
		String qualifier= imports.addImport(currType);
		Name name= ASTNodeFactory.newName(ast, qualifier);
		
		Expression newExpression;
		if (isInstanceMethod) {
			ThisExpression expr= ast.newThisExpression();
			expr.setQualifier(name);
			newExpression= expr;		
		} else {
			newExpression= name;
		}
		
		rewrite.set(invocationNode, MethodInvocation.EXPRESSION_PROPERTY, newExpression, null);

		String label= CorrectionMessages.getFormattedString("UnresolvedElementsSubProcessor.changetoouter.description", ASTResolving.getTypeSignature(currType)); //$NON-NLS-1$	
		Image image= JavaPluginImages.get(JavaPluginImages.IMG_CORRECTION_CHANGE);
		ASTRewriteCorrectionProposal proposal= new ASTRewriteCorrectionProposal(label, context.getCompilationUnit(), rewrite, 8, image);
		proposal.setImportRewrite(imports);
		proposals.add(proposal);	
	}
	

	public static void getConstructorProposals(IInvocationContext context, IProblemLocation problem, Collection proposals) throws CoreException {
		ICompilationUnit cu= context.getCompilationUnit();
		
		CompilationUnit astRoot= context.getASTRoot();
		ASTNode selectedNode= problem.getCoveringNode(astRoot);
		if (selectedNode == null) {
			return;
		}
		
		ITypeBinding targetBinding= null;
		List arguments= null;
		IMethodBinding recursiveConstructor= null;
		
		int type= selectedNode.getNodeType();
		if (type == ASTNode.CLASS_INSTANCE_CREATION) {
			ClassInstanceCreation creation= (ClassInstanceCreation) selectedNode;
			
			IBinding binding= creation.getType().resolveBinding();
			if (binding instanceof ITypeBinding) {
				targetBinding= (ITypeBinding) binding;
				arguments= creation.arguments();		
			}
		} else if (type == ASTNode.SUPER_CONSTRUCTOR_INVOCATION) {
			ITypeBinding typeBinding= Bindings.getBindingOfParentType(selectedNode);
			if (typeBinding != null && !typeBinding.isAnonymous()) {
				targetBinding= typeBinding.getSuperclass();
				arguments= ((SuperConstructorInvocation) selectedNode).arguments();
			}
		} else if (type == ASTNode.CONSTRUCTOR_INVOCATION) {
			ITypeBinding typeBinding= Bindings.getBindingOfParentType(selectedNode);
			if (typeBinding != null && !typeBinding.isAnonymous()) {
				targetBinding= typeBinding;
				arguments= ((ConstructorInvocation) selectedNode).arguments();
				recursiveConstructor= ASTResolving.findParentMethodDeclaration(selectedNode).resolveBinding();
			}			
		}
		if (targetBinding == null) {
			return;
		}
		IMethodBinding[] methods= targetBinding.getDeclaredMethods();
		ArrayList similarElements= new ArrayList();
		for (int i= 0; i < methods.length; i++) {
			IMethodBinding curr= methods[i];
			if (curr.isConstructor() && recursiveConstructor != curr) {
				similarElements.add(curr); // similar elements can contain a implicit default constructor
			}
		}
		
		addParameterMissmatchProposals(context, problem, similarElements, selectedNode, arguments, proposals);
		
		if (targetBinding.isFromSource()) {
			ITypeBinding targetDecl= targetBinding.getTypeDeclaration();
			
			ICompilationUnit targetCU= ASTResolving.findCompilationUnitForBinding(cu, astRoot, targetDecl);
			if (targetCU != null) {
				String[] args= new String[] { ASTResolving.getMethodSignature( ASTResolving.getTypeSignature(targetDecl), getParameterTypes(arguments)) };
				String label= CorrectionMessages.getFormattedString("UnresolvedElementsSubProcessor.createconstructor.description", args); //$NON-NLS-1$
				Image image= JavaElementImageProvider.getDecoratedImage(JavaPluginImages.DESC_MISC_PUBLIC, JavaElementImageDescriptor.CONSTRUCTOR, JavaElementImageProvider.SMALL_SIZE);
				proposals.add(new NewMethodCompletionProposal(label, targetCU, selectedNode, arguments, targetDecl, 5, image));
			}
		}
	}
	
	public static void getAmbiguosTypeReferenceProposals(IInvocationContext context, IProblemLocation problem, Collection proposals) throws CoreException {
		final ICompilationUnit cu= context.getCompilationUnit();
		int offset= problem.getOffset();
		int len= problem.getLength();
		
		IJavaElement[] elements= cu.codeSelect(offset, len);
		for (int i= 0; i < elements.length; i++) {
			IJavaElement curr= elements[i];
			if (curr instanceof IType && !TypeFilter.isFiltered((IType) curr)) {
				String qualifiedTypeName= JavaModelUtil.getFullyQualifiedName((IType) curr);
				
				ImportRewrite imports= new ImportRewrite(cu);
				imports.setFindAmbiguosImports(true);
				imports.addImport(qualifiedTypeName);
				
				String label= CorrectionMessages.getFormattedString("UnresolvedElementsSubProcessor.importexplicit.description", qualifiedTypeName); //$NON-NLS-1$
				Image image= JavaPluginImages.get(JavaPluginImages.IMG_OBJS_IMPDECL);
				CUCorrectionProposal proposal= new CUCorrectionProposal(label, cu,  5, image);
				proposal.setImportRewrite(imports);
				proposals.add(proposal);		
			}
		}
	}
	
	public static void getArrayAccessProposals(IInvocationContext context, IProblemLocation problem, Collection proposals) {
		
		CompilationUnit root= context.getASTRoot();
		ASTNode selectedNode= problem.getCoveringNode(root);
		if (!(selectedNode instanceof MethodInvocation)) {
			return;
		}
		
		MethodInvocation decl= (MethodInvocation) selectedNode;
		SimpleName nameNode= decl.getName();
		String methodName= nameNode.getIdentifier();
		
		IBinding[] bindings= (new ScopeAnalyzer(root)).getDeclarationsInScope(nameNode, ScopeAnalyzer.METHODS);
		for (int i= 0; i < bindings.length; i++) {
			String currName= bindings[i].getName();
			if (NameMatcher.isSimilarName(methodName, currName)) {
				String label= CorrectionMessages.getFormattedString("UnresolvedElementsSubProcessor.arraychangetomethod.description", currName); //$NON-NLS-1$
				proposals.add(new RenameNodeCompletionProposal(label, context.getCompilationUnit(), nameNode.getStartPosition(), nameNode.getLength(), currName, 6));
			}
		}
		// always suggest 'length'
		String lengthId= "length"; //$NON-NLS-1$
		String label= CorrectionMessages.getString("UnresolvedElementsSubProcessor.arraychangetolength.description"); //$NON-NLS-1$
		int offset= nameNode.getStartPosition();
		int length= decl.getStartPosition() + decl.getLength() - offset;
		proposals.add(new RenameNodeCompletionProposal(label, context.getCompilationUnit(), offset, length, lengthId, 7));
	}

	
}
