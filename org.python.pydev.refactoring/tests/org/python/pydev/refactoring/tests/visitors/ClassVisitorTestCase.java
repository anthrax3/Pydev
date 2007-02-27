package org.python.pydev.refactoring.tests.visitors;

import java.util.Iterator;

import org.eclipse.jface.text.Document;
import org.python.pydev.refactoring.ast.adapters.ClassDefAdapter;
import org.python.pydev.refactoring.ast.adapters.ModuleAdapter;
import org.python.pydev.refactoring.ast.visitors.VisitorFactory;
import org.python.pydev.refactoring.ast.visitors.context.ClassDefVisitor;
import org.python.pydev.refactoring.tests.adapter.PythonNatureStub;
import org.python.pydev.refactoring.tests.core.AbstractIOTestCase;

/**
 * @author Ueli Kistler
 */
public class ClassVisitorTestCase extends AbstractIOTestCase {

	public ClassVisitorTestCase(String name) {
		super(name);
	}

	@Override
	public void runTest() throws Throwable {
		StringBuffer buffer = new StringBuffer();
		ModuleAdapter module = VisitorFactory.createModuleAdapter(null, null, new Document(getSource()), new PythonNatureStub());
		ClassDefVisitor visitor = VisitorFactory.createContextVisitor(ClassDefVisitor.class, module.getASTNode(), module, module);
		Iterator<ClassDefAdapter> iter = visitor.iterator();

		buffer.append("# " + visitor.getAll().size() + "\n");
		while (iter.hasNext()) {
			ClassDefAdapter adapter = iter.next();
			buffer.append("# " + adapter.getName() + " " + adapter.isNested() + "\n");
		}
		this.setTestGenerated(buffer.toString().trim());

		assertEquals(3, visitor.getAll().size());
		assertEquals(getExpected(), getGenerated());
	}

	@Override
	public String getExpected() {
		return getResult();
	}

}
