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

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExtension;
import org.eclipse.core.runtime.IExtensionPoint;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.eclipse.equinox.p2.operations.ProvisioningSession;
import org.eclipse.equinox.p2.operations.RepositoryTracker;
import org.eclipse.equinox.p2.ui.ProvisioningUI;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.PlatformUI;
import org.knime.core.node.NodeLogger;
import org.knime.product.branding.IBrandingService;
import org.osgi.framework.Bundle;

import com.knime.licenses.LicenseChecker;
import com.knime.licenses.LicenseException;
import com.knime.licenses.LicenseFeatures;
import com.knime.licenses.LicenseUtil;

/**
 * The BrandingService retrieves the information from the <tt>com.knime.branding.PartnerBranding</tt> extension point
 * and prepares it for the branding
 *
 * @author Ferry Abt
 * @noreference This class is not to be used by clients. Acquire the OSGi service {@link IBrandingService} instead.
 */
public class BrandingService implements IBrandingService {
	private static final NodeLogger LOGGER = NodeLogger.getLogger(BrandingService.class);

	// the values retrieved from the Extension Point
	private final Map<String, String> m_brandingValues = new HashMap<String, String>();

	/**
	 * Creates a new branding service.
	 */
	public BrandingService() {
		try {
			retrieveBrandingInformation();
			brandUpdateSite();
			brandTitle();
		} catch (LicenseException ex) {
			LOGGER.info("No Client Branding license found, branding will not be available.", ex);
		} catch (Exception ex) {
			LOGGER.coding("Branding Service failed to initialize: " + ex.getMessage(), ex);
			m_brandingValues.clear();
		}
	}

	@Override
	public Map<String, String> getBrandingInfo() {
		return m_brandingValues;
	}

	/**
	 * retrieves the branding information of the Extension Point, validates it
	 * and stores it in {@code m_brandingValues}
	 * @throws LicenseException if no license for partner branding is available
	 */
	private void retrieveBrandingInformation() throws LicenseException {
        IExtensionPoint extensionPoint =
            Platform.getExtensionRegistry().getExtensionPoint("com.knime.branding.PartnerBranding");
		IExtension[] extensions = extensionPoint.getExtensions();
		if (extensions.length < 1) {
			return;
		}
        LicenseChecker checker = new LicenseUtil(LicenseFeatures.ClientBranding);
        checker.checkLicense();

		if (extensions.length > 1) {
			LOGGER.warn("More than one branding extension found. Only " + extensions[0].getLabel()
					+ " will be used.");
		}
		IExtension iExtension = extensions[0];

		m_brandingValues.put("PartnerName", iExtension.getLabel());
		IConfigurationElement[] configurationElements = iExtension.getConfigurationElements();
		for (IConfigurationElement element : configurationElements) {
			if ("WelcomePage".equals(element.getName())) {
				String wtgfhLink = element.getAttribute("WhereToGoFromHereLink");
				String wtgfhText = element.getAttribute("WhereToGoFromHereText");
				String logo = element.getAttribute("Logo");
				String introText = element.getAttribute("IntroText");
				String customTips = element.getAttribute("CustomTips");
				if (!StringUtils.isEmpty(wtgfhLink) && !StringUtils.isEmpty(wtgfhText)) {
					try {
						new URL(wtgfhLink);
						m_brandingValues.put("WhereToGoFromHereLink", wtgfhLink);
						m_brandingValues.put("WhereToGoFromHereText", wtgfhText);
					} catch (MalformedURLException e) {
						LOGGER.coding("WhereToGoFromHereLink malformed: " + wtgfhLink);
					}
				}
				Bundle bundle = Platform.getBundle(iExtension.getContributor().getName());
				if (!StringUtils.isEmpty(logo)) {
					try {
						URL resolve = FileLocator.resolve(FileLocator.find(bundle, new Path(logo), null));
						m_brandingValues.put("Logo", resolve.toString());
					} catch (IOException e) {
						LOGGER.coding("Failed to resolve logo-path: " + e.getMessage());
					}
				}
				if (!StringUtils.isEmpty(introText)) {
					try {
						URL resolve = FileLocator.resolve(FileLocator.find(bundle, new Path(introText), null));
						m_brandingValues.put("IntroText", resolve.toString());
					} catch (IOException e) {
						LOGGER.coding("Failed to resolve path to intro text: " + e.getMessage());
					}
				}
				if (!StringUtils.isEmpty(customTips)) {
					try {
						new URL(customTips);
						m_brandingValues.put("CustomTips", customTips);
					} catch (MalformedURLException e) {
						LOGGER.coding("Custom Tips&Tricks-URL malformed: " + customTips);
					}
				}
			} else if ("UpdateSite".equals(element.getName())) {
				String uri = element.getAttribute("URI");
				String name = element.getAttribute("Name");
				if (!StringUtils.isEmpty(uri) && !StringUtils.isEmpty(name)) {
					m_brandingValues.put("UpdateSite_URI", uri);
					m_brandingValues.put("UpdateSite_Name", name);
				}
			} else if ("WindowTitle".equals(element.getName())) {
				String text = element.getAttribute("Text");
				if (!StringUtils.isEmpty(text)) {
					m_brandingValues.put("WindowTitle", text);
				}
			} else if ("HelpContact".equals(element.getName())) {
				String helpAddress = element.getAttribute("HelpAddress");
				String buttonText = element.getAttribute("ButtonText");
				if (!StringUtils.isEmpty(helpAddress) && !StringUtils.isEmpty(buttonText)) {
					m_brandingValues.put("helpContact", helpAddress);
					m_brandingValues.put("helpButton", buttonText);
				}
			}
		}
	}

	/**
	 * If provided and valid it adds the custom Update Site to the list of
	 * available software site. To prevent duplication it compares the URLs.
	 */
	private void brandUpdateSite() {
		if (StringUtils.isEmpty(m_brandingValues.get("UpdateSite_URI"))) {
			return;
		}
		try {
			ProvisioningUI defaultUI = ProvisioningUI.getDefaultUI();
			RepositoryTracker repositoryTracker = defaultUI.getRepositoryTracker();
			ProvisioningSession session = defaultUI.getSession();

			boolean added = false;
			for (URI uri : repositoryTracker.getKnownRepositories(session)) {
				if (uri.equals(new URI(m_brandingValues.get("UpdateSite_URI")))) {
					added = true;
					break;
				}
			}
			if (!added) {
				repositoryTracker.addRepository(new URI(m_brandingValues.get("UpdateSite_URI")),
						m_brandingValues.get("UpdateSite_Name"), session);
			}
		} catch (URISyntaxException e) {
			LOGGER.coding("Partner Update Site is not a valid URI: " + m_brandingValues.get("UpdateSite_URI"));
		}
	}

	/**
	 * If provided it appends the text to the window title.
	 */
	private void brandTitle() {
		if (StringUtils.isEmpty(m_brandingValues.get("WindowTitle"))) {
			return;
		}
		Display.getDefault().asyncExec(new Runnable() {
			@Override
			public void run() {
				Shell shell = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell();
				shell.setText(shell.getText() + " " + m_brandingValues.get("WindowTitle"));
			}
		});
	}
}
