package org.python.pydev.refactoring.tests.adapter;

import org.eclipse.jface.text.Document;
import org.python.pydev.refactoring.ast.adapters.ModuleAdapter;
import org.python.pydev.refactoring.ast.visitors.VisitorFactory;
import org.python.pydev.refactoring.core.FQIdentifier;
import org.python.pydev.refactoring.tests.core.AbstractIOTestCase;

import com.thoughtworks.xstream.XStream;

public class ModuleAdapterTestCase extends AbstractIOTestCase {

	public ModuleAdapterTestCase(String name) {
		super(name);
	}

	@Override
	public void runTest() throws Throwable {
		StringBuffer buffer = new StringBuffer();

		ModuleAdapterTestConfig config = null;
		XStream xstream = new XStream();
		xstream.alias("config", ModuleAdapterTestConfig.class);

		ModuleAdapter module = VisitorFactory.createModuleAdapter(null, null, new Document(getSource()), new PythonNatureStub());
		if (getConfig().length() > 0) {
			config = (ModuleAdapterTestConfig) xstream.fromXML(getConfig());
		} else {
			fail("Could not unserialize configuration");
		}

		for (String identifier : config.resolveNames) {
			for (FQIdentifier id : module.resolveFullyQualified(identifier)) {
				buffer.append("# " + identifier + " -> " + id.getFQName());
				buffer.append("\n");
			}
		}
		buffer.append("# Imported regular modules (Alias, Realname)");
		for (String aliasModule : module.getRegularImportedModules().keySet()) {
			buffer.append("\n# " + aliasModule + " " + module.getRegularImportedModules().get(aliasModule));
		}

		buffer.append("\n");
		buffer.append("# AliasToIdentifier (Module, Realname, Alias)");
		for (FQIdentifier identifier : module.getAliasToIdentifier()) {
			buffer.append("\n# " + identifier.getModule() + " " + identifier.getRealName() + " " + identifier.getAlias());
		}

		this.setTestGenerated(buffer.toString().trim());
		assertEquals(getExpected(), getGenerated());
	}

	@Override
	public String getExpected() {
		return getResult();
	}

}
