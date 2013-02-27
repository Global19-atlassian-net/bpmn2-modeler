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
 * @author Innar Made
 ******************************************************************************/
package org.eclipse.bpmn2.modeler.ui.preferences;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.bpmn2.modeler.core.preferences.Bpmn2Preferences;
import org.eclipse.bpmn2.modeler.core.preferences.ToolEnablement;
import org.eclipse.bpmn2.modeler.core.preferences.ToolEnablementPreferences;
import org.eclipse.bpmn2.modeler.core.runtime.ModelEnablementDescriptor;
import org.eclipse.bpmn2.modeler.core.runtime.TargetRuntime;
import org.eclipse.bpmn2.modeler.ui.Activator;
import org.eclipse.core.databinding.DataBindingContext;
import org.eclipse.core.databinding.observable.list.WritableList;
import org.eclipse.core.resources.IProject;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.jface.viewers.CheckStateChangedEvent;
import org.eclipse.jface.viewers.CheckboxTreeViewer;
import org.eclipse.jface.viewers.ICheckStateListener;
import org.eclipse.jface.viewers.ICheckStateProvider;
import org.eclipse.jface.viewers.IElementComparer;
import org.eclipse.jface.viewers.ILabelProvider;
import org.eclipse.jface.viewers.ILabelProviderListener;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeItem;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.dialogs.PropertyPage;
import org.osgi.service.prefs.BackingStoreException;

public class ToolEnablementPropertyPage extends PropertyPage {

	private DataBindingContext m_bindingContext;

	private ToolEnablementPreferences toolEnablementPreferences;
	private Bpmn2Preferences bpmn2Preferences;
	private final List<ToolEnablement> tools = new ArrayList<ToolEnablement>();
	private Object[] toolsEnabled;
	private CheckboxTreeViewer checkboxTreeViewer;
	private Tree checkboxTree;
	private boolean profileCopied;

	private WritableList writableList;

	/**
	 * Create the property page.
	 */
	public ToolEnablementPropertyPage() {
		setTitle("Tool Enablement");
	}

	/**
	 * Create contents of the property page.
	 * 
	 * @param parent
	 */
	@Override
	public Control createContents(Composite parent) {
		initData();

		Composite container = new Composite(parent, SWT.NULL);
		container.setLayout(new GridLayout(3, false));
		container.setLayoutData(new GridData(SWT.FILL, SWT.LEFT, true, false, 1, 1));

		final Label lblProfile = new Label(container, SWT.NONE);
		lblProfile.setText("Default Tool Enablement Profile:");
		lblProfile.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false, 2, 1));

		final Combo cboProfile = new Combo(container, SWT.READ_ONLY);
		cboProfile.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, true, false, 1, 1));
		TargetRuntime currentRuntime = bpmn2Preferences.getRuntime();
		String currentProfile = bpmn2Preferences.getDefaultModelEnablementProfile();
		int i = 1;
		int iSelected = -1;
		cboProfile.add("");
		for (ModelEnablementDescriptor md : currentRuntime.getModelEnablements()) {
			String profile = md.getProfile();
			String text = profile;
			if (text==null || text.isEmpty())
				text = "Unnamed " + (i+1);
			cboProfile.add(text);
			cboProfile.setData(Integer.toString(i), md);
			if (iSelected<0 && (currentProfile!=null && currentProfile.equals(profile)))
				cboProfile.select(iSelected = i);
			++i;
		}

		final Button btnOverride = new Button(container,SWT.CHECK);
		btnOverride.setText("Override default tool enablements with these settings:");
		btnOverride.setSelection(bpmn2Preferences.getOverrideModelEnablements());
		btnOverride.setLayoutData(new GridData(SWT.LEFT, SWT.TOP, true, false, 3, 1));

		checkboxTreeViewer = new CheckboxTreeViewer(container, SWT.BORDER);
		checkboxTree = checkboxTreeViewer.getTree();
		GridData data = new GridData(SWT.FILL, SWT.FILL, true, true, 3, 1);
		data.heightHint = 200;
		checkboxTree.setLayoutData(data);
		checkboxTreeViewer.setCheckStateProvider(new ICheckStateProvider() {
			@Override
			public boolean isChecked(Object element) {
				if (element instanceof ToolEnablement) {
					return ((ToolEnablement)element).getEnabled();
				}
				return false;
			}

			@Override
			public boolean isGrayed(Object element) {
				if (element instanceof ToolEnablement) {
					ToolEnablement te = (ToolEnablement)element;
					if (te.getTool() instanceof EClass) {
						int countEnabled = 0;
						for (ToolEnablement child : te.getChildren()) {
							if (child.getEnabled())
								++countEnabled;
						}
						return countEnabled != te.getChildren().size();
					}
				}
				return false;
			}
			
		});
		checkboxTreeViewer.addCheckStateListener(new ICheckStateListener() {
			@Override
			public void checkStateChanged(CheckStateChangedEvent event) {
				boolean checked = event.getChecked();
				ToolEnablement element = (ToolEnablement)event.getElement();
				element.setEnabled(checked);
				if (element.getTool() instanceof EClass) {
					boolean grayed = false;
					checkboxTreeViewer.setSubtreeChecked(element, checked);
					if (!checked) {
						// move from checked state to grayed state if
						// all children are currently selected
						int countEnabled = 0;
						for (ToolEnablement child : element.getChildren()) {
							if (child.getEnabled())
								++countEnabled;
						}
						if (countEnabled == element.getChildren().size()) {
							checkboxTreeViewer.setGrayChecked(element, true);
							grayed = true;
						}
					}
					for (ToolEnablement child : element.getChildren()) {
						child.setEnabled(checked);
					}
					if (!grayed) {
						checkboxTreeViewer.setChecked(element, checked);
						checkboxTreeViewer.setGrayed(element, false);
					}
				}
				else {
					ToolEnablement parent = element.getParent();
					int countEnabled = 0;
					for (ToolEnablement child : parent.getChildren()) {
						if (child.getEnabled())
							++countEnabled;
					}
					if (countEnabled==0) {
						checkboxTreeViewer.setChecked(parent, false);
						parent.setEnabled(false);
					}
					else if (countEnabled == parent.getChildren().size()) {
						checkboxTreeViewer.setChecked(parent, true);
						checkboxTreeViewer.setGrayed(parent, false);
						parent.setEnabled(true);
					}
					else {
						checkboxTreeViewer.setGrayChecked(parent, true);
						parent.setEnabled(true);
					}
				}
			}
		});
				
		final Button btnCopy = new Button(container,SWT.FLAT);
		btnCopy.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false, 1, 1));
		btnCopy.setText("Copy");

		final Label lblCopy = new Label(container, SWT.NONE);
		lblCopy.setText("all enablements from Target Runtime:");
		lblCopy.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false, 1, 1));

		final Combo cboCopy = new Combo(container, SWT.READ_ONLY);
		cboCopy.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, true, false, 1, 1));
		i = 0;
		iSelected = -1;
		for (TargetRuntime rt : TargetRuntime.getAllRuntimes()) {
			for (ModelEnablementDescriptor md : rt.getModelEnablements()) {
				String text = rt.getName();
				if (md.getType()!=null)
					text += " - " + md.getType();
				if (md.getProfile()!=null)
					text += " (" + md.getProfile() + ")";
				cboCopy.add(text);
				cboCopy.setData(Integer.toString(i), md);
				if (rt == currentRuntime && iSelected<0)
					cboCopy.select(iSelected = i);
				++i;
			}
		}

		Composite importExportButtons = new Composite(container, SWT.NONE);
		importExportButtons.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 3, 1));
		importExportButtons.setLayout(new FillLayout());

		Button btnImportProfile = new Button(importExportButtons, SWT.NONE);
		btnImportProfile.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				FileDialog dialog = new FileDialog(PlatformUI.getWorkbench().getDisplay().getActiveShell(), SWT.NULL);
				String path = dialog.open();
				if (path != null) {
					try {
						tools.clear();
						toolEnablementPreferences.importPreferences(path);
						reloadPreferences();
						checkboxTreeViewer.refresh();
						restoreDefaults();
					} catch (Exception e1) {
						Activator.showErrorWithLogging(e1);
					}
				}
			}
		});
		btnImportProfile.setText("Import Profile ...");

		Button btnExportProfile = new Button(importExportButtons, SWT.NONE);
		btnExportProfile.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				FileDialog dialog = new FileDialog(PlatformUI.getWorkbench().getDisplay().getActiveShell(), SWT.SAVE);
				String path = dialog.open();
				if (path != null) {
					try {
						String runtimeId = null;
						String type = null;
						String profile = null;
						if (profileCopied) {
							int i = cboCopy.getSelectionIndex();
							ModelEnablementDescriptor md = (ModelEnablementDescriptor)cboCopy.getData(""+i);
							runtimeId = md.getRuntime().getId();
							profile = md.getProfile();
							type = md.getType();
						}
						toolEnablementPreferences.exportPreferences(runtimeId, type, profile, path);
					} catch (Exception e1) {
						Activator.showErrorWithLogging(e1);
					}
				}
			}
		});
		btnExportProfile.setText("Export Profile ...");

		checkboxTreeViewer.setComparer(new IElementComparer() {

			@Override
			public boolean equals(Object a, Object b) {
				return a == b;
			}

			@Override
			public int hashCode(Object element) {
				return System.identityHashCode(element);
			}
		});
		checkboxTreeViewer.setUseHashlookup(true);
		m_bindingContext = initDataBindings();
		
		cboProfile.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				bpmn2Preferences.setDefaultModelEnablementProfile( cboProfile.getText() );
			}
		});

		boolean enable = btnOverride.getSelection();
		checkboxTree.setEnabled(enable);
		btnCopy.setEnabled(enable);
		lblCopy.setEnabled(enable);
		cboCopy.setEnabled(enable);

		btnOverride.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				boolean enable = btnOverride.getSelection();
				checkboxTree.setEnabled(enable);
				bpmn2Preferences.setOverrideModelEnablements(enable);
				btnCopy.setEnabled(enable);
				lblCopy.setEnabled(enable);
				cboCopy.setEnabled(enable);
			}
		});
		
		btnCopy.addSelectionListener(new SelectionAdapter() {
			
			@Override
			public void widgetSelected(SelectionEvent e) {
				int i = cboCopy.getSelectionIndex();
				ModelEnablementDescriptor md = (ModelEnablementDescriptor) cboCopy.getData(Integer.toString(i)); 
				toolEnablementPreferences.setEnablements(md);
				
				reloadPreferences();
				checkboxTreeViewer.refresh();
				restoreDefaults();
				
				profileCopied = true;
			}
		});


		restoreDefaults();

		return container;
	}

	private void restoreDefaults() {
		checkboxTreeViewer.setCheckedElements(toolsEnabled);
	}

	@Override
	protected void performDefaults() {
		super.performDefaults();
		restoreDefaults();
	}

	private void initData() {
		toolEnablementPreferences = ToolEnablementPreferences.getPreferences((IProject) getElement().getAdapter(IProject.class));
		IProject project = (IProject)getElement().getAdapter(IProject.class);
		bpmn2Preferences = Bpmn2Preferences.getInstance(project);

		reloadPreferences();
	}

	private void reloadPreferences() {
		tools.clear();
		tools.addAll(toolEnablementPreferences.getAllElements());
		ArrayList<ToolEnablement> tEnabled = new ArrayList<ToolEnablement>();
		for (ToolEnablement tool : tools) {
			if (tool.getEnabled()) {
				tEnabled.add(tool);
			}
			ArrayList<ToolEnablement> children = tool.getChildren();
			for (ToolEnablement t : children) {
				if (t.getEnabled()) {
					tEnabled.add(t);
				}
			}
		}
		toolsEnabled = tEnabled.toArray();
	}

	@Override
	public boolean performOk() {
		setErrorMessage(null);
		try {
			updateToolEnablement(tools, Arrays.asList(checkboxTreeViewer.getCheckedElements()));
			bpmn2Preferences.save();
		} catch (BackingStoreException e) {
			Activator.showErrorWithLogging(e);
		}
		return true;
	}

	private void updateToolEnablement(List<ToolEnablement> saveables, List<Object> enabled)
			throws BackingStoreException {
		for (ToolEnablement t : saveables) {
			toolEnablementPreferences.setEnabled(t, enabled.contains(t));
			for (ToolEnablement c : t.getChildren()) {
				toolEnablementPreferences.setEnabled(c, enabled.contains(c));
			}
		}
		toolEnablementPreferences.flush();
	}

	protected DataBindingContext initDataBindings() {
		DataBindingContext bindingContext = new DataBindingContext();
		//
		checkboxTreeViewer.setContentProvider(new ITreeContentProvider() {

			@Override
			public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
			}

			@Override
			public void dispose() {
			}

			@Override
			public boolean hasChildren(Object element) {
				if (element instanceof ToolEnablement) {
					return !((ToolEnablement) element).getChildren().isEmpty();
				}
				return false;
			}

			@Override
			public Object getParent(Object element) {
				if (element instanceof ToolEnablement) {
					return ((ToolEnablement) element).getParent();
				}
				return null;
			}

			@Override
			public Object[] getElements(Object inputElement) {
				if (inputElement instanceof WritableList) {
					return ((WritableList) inputElement).toArray();
				}
				return null;
			}

			@Override
			public Object[] getChildren(Object parentElement) {
				if (parentElement instanceof ToolEnablement) {
					return ((ToolEnablement) parentElement).getChildren().toArray();
				}
				return null;
			}
		});

		checkboxTreeViewer.setLabelProvider(new ILabelProvider() {
			@Override
			public void removeListener(ILabelProviderListener listener) {
			}

			@Override
			public boolean isLabelProperty(Object element, String property) {
				return false;
			}

			@Override
			public void dispose() {

			}

			@Override
			public void addListener(ILabelProviderListener listener) {
			}

			@Override
			public Image getImage(Object element) {
				return null;
			}

			@Override
			public String getText(Object element) {
				if (element instanceof ToolEnablement) {
					return ((ToolEnablement) element).getName();
				}
				return null;
			}
		});
		writableList = new WritableList(tools, ToolEnablement.class);
		checkboxTreeViewer.setInput(writableList);
		//
		return bindingContext;
	}

}
