/*******************************************************************************
 * Copyright (c) 2011, 2012 Red Hat, Inc.
 *  All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 *
 * @author Bob Brodt
 ******************************************************************************/

package org.eclipse.bpmn2.modeler.runtime.jboss.jbpm5.property;


import java.util.List;

import org.eclipse.bpmn2.Bpmn2Package;
import org.eclipse.bpmn2.Definitions;
import org.eclipse.bpmn2.Process;
import org.eclipse.bpmn2.RootElement;
import org.eclipse.bpmn2.modeler.core.merrimac.clad.AbstractBpmn2PropertySection;
import org.eclipse.bpmn2.modeler.core.merrimac.clad.AbstractListComposite;
import org.eclipse.bpmn2.modeler.core.merrimac.clad.AbstractPropertiesProvider;
import org.eclipse.bpmn2.modeler.core.merrimac.clad.ListCompositeColumnProvider;
import org.eclipse.bpmn2.modeler.core.merrimac.clad.TableColumn;
import org.eclipse.bpmn2.modeler.core.utils.ModelUtil;
import org.eclipse.bpmn2.modeler.runtime.jboss.jbpm5.model.GlobalType;
import org.eclipse.bpmn2.modeler.runtime.jboss.jbpm5.model.ModelFactory;
import org.eclipse.bpmn2.modeler.runtime.jboss.jbpm5.model.ModelPackage;
import org.eclipse.bpmn2.modeler.ui.property.ExtensionValueListComposite;
import org.eclipse.bpmn2.modeler.ui.property.diagrams.DataItemsDetailComposite;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.swt.widgets.Composite;

/**
 * @author Bob Brodt
 *
 */
public class JbpmDataItemsDetailComposite extends DataItemsDetailComposite {

	ExtensionValueListComposite globalsTable;
	
	/**
	 * @param section
	 */
	public JbpmDataItemsDetailComposite(AbstractBpmn2PropertySection section) {
		super(section);
	}

	public JbpmDataItemsDetailComposite(Composite parent, int style) {
		super(parent, style);
	}
	

	@Override
	public AbstractPropertiesProvider getPropertiesProvider(EObject object) {
		if (propertiesProvider==null) {
			propertiesProvider = new AbstractPropertiesProvider(object) {
				String[] properties = new String[] {
						"rootElements#Process.global",
						"rootElements#Process.properties",
						"rootElements#Process.resources",
				};
				
				@Override
				public String[] getProperties() {
					return properties; 
				}
			};
		}
		return propertiesProvider;
	}

	@Override
	public void cleanBindings() {
		super.cleanBindings();
		globalsTable = null;
	}

	@Override
	public void createBindings(EObject be) {
		if (be instanceof Definitions) {
			Definitions definitions = (Definitions)be;
			for (RootElement re : definitions.getRootElements()) {
				if (re instanceof Process) {
					Process process = (Process)re;
					globalsTable = new ExtensionValueListComposite(this, AbstractListComposite.DEFAULT_STYLE) {
						
						@Override
						protected EObject addListItem(EObject object, EStructuralFeature feature) {
							// generate a unique global variable name
							String base = "globalVar";
							int suffix = 1;
							String name = base + suffix;
							for (;;) {
								boolean found = false;
								for (Object g : ModelUtil.getAllExtensionAttributeValues(object, GlobalType.class)) {
									if (name.equals(((GlobalType)g).getIdentifier()) ) {
										found = true;
										break;
									}
								}
								if (!found)
									break;
								name = base + ++suffix;
							}
							
							GlobalType newGlobal = (GlobalType)ModelFactory.eINSTANCE.create(listItemClass);
							newGlobal.setIdentifier(name);
							addExtensionValue(newGlobal);
							return newGlobal;
						}

						public ListCompositeColumnProvider getColumnProvider(EObject object, EStructuralFeature feature) {
							if (columnProvider==null) {
								columnProvider = new ListCompositeColumnProvider(this);
								columnProvider.add(new TableColumn(object,ModelPackage.eINSTANCE.getGlobalType_Identifier()));
								columnProvider.add(new TableColumn(object,ModelPackage.eINSTANCE.getGlobalType_Type())).setHeaderText("Data Type");
							}
							return columnProvider;
						}
					};
					globalsTable.bindList(process, ModelPackage.eINSTANCE.getDocumentRoot_Global());
					globalsTable.setTitle("Global List for "+ModelUtil.getLongDisplayName(process));
				}
			}
		}
		super.createBindings(be);
	}
}
