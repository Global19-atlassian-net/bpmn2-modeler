package org.eclipse.bpmn2.modeler.core.utils;

import org.eclipse.osgi.util.NLS;

public class Messages extends NLS {
	private static final String BUNDLE_NAME = "org.eclipse.bpmn2.modeler.core.utils.messages"; //$NON-NLS-1$
	public static String ErrorUtils_Title;
	public static String ModelUtil_Choreography_Diagram;
	public static String ModelUtil_Choreograpy_Diagram;
	public static String ModelUtil_Collaboration_Diagram;
	public static String ModelUtil_Internal_Error;
	public static String ModelUtil_Process_Diagram;
	public static String ModelUtil_Unknown_Diagram_Type;
	static {
		// initialize resource bundle
		NLS.initializeMessages(BUNDLE_NAME, Messages.class);
	}

	private Messages() {
	}
}