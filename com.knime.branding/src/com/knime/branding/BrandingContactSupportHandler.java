/* ------------------------------------------------------------------
 * This source code, its documentation and all appendant files
 * are protected by copyright law. All rights reserved.
 *
 * Copyright by KNIME AG, Zurich, Switzerland
 *
 * You may not modify, publish, transmit, transfer or sell, reproduce,
 * create derivative works from, distribute, perform, display, or in
 * any way exploit any of the content, in whole or in part, except as
 * otherwise expressly permitted in writing by the copyright owner or
 * as specified in the license file distributed with this product.
 *
 * If you have any questions please contact the copyright holder:
 * website: www.knime.com
 * email: contact@knime.com
 * ---------------------------------------------------------------------
 *
 * History
 *   May 21, 2016 (Ferry Abt): created
 */
package com.knime.branding;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.commands.ICommandService;
import org.eclipse.ui.commands.IElementUpdater;
import org.eclipse.ui.menus.UIElement;
import org.knime.core.node.NodeLogger;
import org.knime.product.branding.IBrandingService;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.ServiceReference;

/**
 * Handler for the custom Help-entry
 * 
 * @author Ferry Abt
 *
 */
public class BrandingContactSupportHandler extends AbstractHandler implements IElementUpdater {

	private static final NodeLogger LOGGER = NodeLogger.getLogger(BrandingContactSupportHandler.class);

	private URI m_helpContact;
	/**
	 * {@code true} if the branding info has been retrieved
	 */
	private boolean m_retrieved;
	private String m_helpText;

	/**
	 * Is it an Email-contact? (web-address otherwise)
	 */
	private boolean m_mail;

	/**
	 * triggers a mailto-event
	 */
	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		if (m_retrieved) {
			try {
				if (m_mail) {
					Desktop.getDesktop().mail(m_helpContact);
				} else {
					Desktop.getDesktop().browse(m_helpContact);
				}
			} catch (IOException e) {
				LOGGER.info("Failed to contact support due to: " + e.getMessage());
			}
		}
		return null;
	}

	/**
	 * {@code true} if provided by via the com.knime.branding-Extension Point
	 */
	@Override
	public boolean isEnabled() {
		// prevent crawling the service multiple times.
		if (m_retrieved) {
			return true;
		}

        BundleContext context = FrameworkUtil.getBundle(getClass()).getBundleContext();
        ServiceReference<?> serviceReference = context.getServiceReference(IBrandingService.class.getName());
        if (serviceReference == null) {
        	return false;
        }
        
        try {
            IBrandingService service = (IBrandingService)context.getService(serviceReference);
            if (service == null) {
            	return false;
            }
		
			Map<String, String> brandingInfo = service.getBrandingInfo();
			if (brandingInfo.containsKey("helpContact") && brandingInfo.containsKey("helpButton")) {
				String helpAdress = brandingInfo.get("helpContact");
				// There is a branded help contact
				try {
					m_helpContact = new URI(helpAdress);
				} catch (URISyntaxException e) {
					LOGGER.coding("Branded contact-URI invalid: " + e.getMessage());
					return false;
				}
				m_mail = helpAdress.startsWith("mailto");
				m_helpText = brandingInfo.get("helpButton");
	
				// label the custom menu element
				ICommandService commandService = PlatformUI.getWorkbench().getActiveWorkbenchWindow()
						.getService(ICommandService.class);
				if (commandService != null) {
					commandService.refreshElements("com.knime.branding.helpButton", null);
				}
				return true;
			} else {
				return false;
			}
        } finally {
        	context.ungetService(serviceReference);
        }
	}

	@Override
	public void updateElement(UIElement element, @SuppressWarnings("rawtypes") Map parameters) {
		if (m_retrieved) {
			element.setText(m_helpText);
		}
	}

}
