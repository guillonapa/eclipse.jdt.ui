package org.eclipse.jdt.ui.tests.refactoring;

import java.util.Hashtable;

import junit.framework.Test;
import junit.framework.TestSuite;

import org.eclipse.core.runtime.NullProgressMonitor;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.Modifier;

import org.eclipse.jdt.ui.tests.refactoring.infra.SourceCompareUtil;
import org.eclipse.jdt.ui.tests.refactoring.infra.TextRangeUtil;

import org.eclipse.jdt.internal.corext.refactoring.base.RefactoringStatus;
import org.eclipse.jdt.internal.corext.refactoring.code.ConvertAnonymousToNestedRefactoring;

public class ConvertAnonymousToNestedTests extends RefactoringTest {

	private static final Class clazz= ConvertAnonymousToNestedTests.class;
	private static final String REFACTORING_PATH= "ConvertAnonymousToNested/";

	private Object fCompactPref; 
		
	public ConvertAnonymousToNestedTests(String name) {
		super(name);
	} 
	
	protected String getRefactoringPath() {
		return REFACTORING_PATH;
	}
	
	public static Test suite() {
		return new MySetup(new TestSuite(clazz));
	}
	
	private String getSimpleTestFileName(boolean canInline, boolean input){
		String fileName = "A_" + getName();
		if (canInline)
			fileName += input ? "_in": "_out";
		return fileName + ".java"; 
	}
	
	private String getTestFileName(boolean canConvert, boolean input){
		String fileName= TEST_PATH_PREFIX + getRefactoringPath();
		fileName += (canConvert ? "canConvert/": "cannotConvert/");
		return fileName + getSimpleTestFileName(canConvert, input);
	}
	
	private String getFailingTestFileName(){
		return getTestFileName(false, false);
	}
	private String getPassingTestFileName(boolean input){
		return getTestFileName(true, input);
	}

	protected ICompilationUnit createCUfromTestFile(IPackageFragment pack, boolean canConvert, boolean input) throws Exception {
		return createCU(pack, getSimpleTestFileName(canConvert, input), getFileContents(getTestFileName(canConvert, input)));
	}

	protected void setUp() throws Exception {
		super.setUp();
		Hashtable options= JavaCore.getOptions();
		fCompactPref= options.get(JavaCore.FORMATTER_COMPACT_ASSIGNMENT);
		options.put(JavaCore.FORMATTER_COMPACT_ASSIGNMENT, JavaCore.COMPACT);
		JavaCore.setOptions(options);
	}
	
	protected void tearDown() throws Exception {
		super.tearDown();
		Hashtable options= JavaCore.getOptions();
		options.put(JavaCore.FORMATTER_COMPACT_ASSIGNMENT, fCompactPref);
		JavaCore.setOptions(options);	
	}

	private void helper1(int startLine, int startColumn, int endLine, int endColumn, boolean makeStatic, boolean makeFinal, String className, int visibility) throws Exception{
		ICompilationUnit cu= createCUfromTestFile(getPackageP(), true, true);
		ISourceRange selection= TextRangeUtil.getSelection(cu, startLine, startColumn, endLine, endColumn);
		ConvertAnonymousToNestedRefactoring ref= new ConvertAnonymousToNestedRefactoring(cu, selection.getOffset(), selection.getLength());

		RefactoringStatus preconditionResult= ref.checkActivation(new NullProgressMonitor());	
		if (preconditionResult.isOK())
			preconditionResult= null;
		assertEquals("activation was supposed to be successful", null, preconditionResult);

		ref.setClassName(className);
		ref.setDeclareFinal(makeFinal);
		ref.setDeclareStatic(makeStatic);
		ref.setVisibility(visibility);
		
		if (preconditionResult == null)
			preconditionResult= ref.checkInput(new NullProgressMonitor());
		else	
			preconditionResult.merge(ref.checkInput(new NullProgressMonitor()));
		if (preconditionResult.isOK())
			preconditionResult= null;
		assertEquals("precondition was supposed to pass", null, preconditionResult);

		
		performChange(ref.createChange(new NullProgressMonitor()));
		
		IPackageFragment pack= (IPackageFragment)cu.getParent();
		String newCuName= getSimpleTestFileName(true, true);
		ICompilationUnit newcu= pack.getCompilationUnit(newCuName);
		assertTrue(newCuName + " does not exist", newcu.exists());
		//assertEquals("incorrect extraction", getFileContents(getTestFileName(true, false)), newcu.getSource());
		SourceCompareUtil.compare(newcu.getSource(), getFileContents(getTestFileName(true, false)));

	}
	
//	private void failHelper1(int startLine, int startColumn, int endLine, int endColumn, boolean replaceAll, boolean makeFinal, String tempName, int expectedStatus) throws Exception{
//		ICompilationUnit cu= createCUfromTestFile(getPackageP(), false, true);
//		ISourceRange selection= TextRangeUtil.getSelection(cu, startLine, startColumn, endLine, endColumn);
//		ExtractTempRefactoring ref= new ExtractTempRefactoring(cu, selection.getOffset(), selection.getLength(), 
//																									JavaPreferencesSettings.getCodeGenerationSettings());
//		
//		ref.setReplaceAllOccurrences(replaceAll);
//		ref.setDeclareFinal(makeFinal);
//		ref.setTempName(tempName);
//		RefactoringStatus result= performRefactoring(ref);
//		assertNotNull("precondition was supposed to fail", result);
//		assertEquals("status", expectedStatus, result.getSeverity());
//	}	

	//--- TESTS
	
	public void test0() throws Exception{
		helper1(5, 17, 5, 17, true, true, "Inner", Modifier.PRIVATE);
	}

	public void test1() throws Exception{
		helper1(5, 17, 5, 17, true, true, "Inner", Modifier.PUBLIC);
	}

	public void test2() throws Exception{
		helper1(5, 17, 5, 17, false, true, "Inner", Modifier.PUBLIC);
	}

	public void test3() throws Exception{
		helper1(5, 17, 5, 17, false, false, "Inner", Modifier.PUBLIC);
	}

	public void test4() throws Exception{
		helper1(7, 17, 7, 17, true, true, "Inner", Modifier.PRIVATE);
	}

	public void test5() throws Exception{
		helper1(7, 17, 7, 19, true, true, "Inner", Modifier.PRIVATE);
	}

	public void test6() throws Exception{
		helper1(8, 13, 9, 14, true, true, "Inner", Modifier.PRIVATE);
	}

	public void test7() throws Exception{
		helper1(7, 18, 7, 18, true, true, "Inner", Modifier.PRIVATE);
	}

	public void test8() throws Exception{
		helper1(8, 14, 8, 15, false, true, "Inner", Modifier.PRIVATE);
	}

	public void test9() throws Exception{
		helper1(8, 13, 8, 14, false, true, "Inner", Modifier.PRIVATE);
	}

	public void test10() throws Exception{
		helper1(7, 15, 7, 16, true, true, "Inner", Modifier.PRIVATE);
	}

	public void test11() throws Exception{
		helper1(5, 15, 5, 17, true, true, "Inner", Modifier.PRIVATE);
	}

	public void test12() throws Exception{
		helper1(8, 9, 10, 10, true, true, "Inner", Modifier.PRIVATE);
	}

	public void test13() throws Exception{
		helper1(6, 28, 6, 28, true, true, "Inner", Modifier.PRIVATE);
	}

	public void test14() throws Exception{
		helper1(5, 13, 5, 23, true, true, "Inner", Modifier.PRIVATE);
	}

	public void test15() throws Exception{
		helper1(7, 26, 7, 26, true, true, "Inner", Modifier.PRIVATE);
	}

	public void test16() throws Exception{
		helper1(4, 10, 4, 26, true, true, "Inner", Modifier.PRIVATE);
	}
	
	public void test17() throws Exception{
		helper1(6, 16, 6, 19, true, true, "Inner", Modifier.PRIVATE);
	}

}
