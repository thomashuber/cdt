/********************************************************************************
 * Copyright (c) 2006 Symbian Software Ltd. All rights reserved.
 * This program and the accompanying materials are made available under the terms
 * of the Eclipse Public License v1.0 which accompanies this distribution, and is 
 * available at http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *   Javier Montalvo Or�s (Symbian) - initial API and implementation
 ********************************************************************************/

package org.eclipse.rse.discovery;

import java.util.Enumeration;
import java.util.Iterator;
import java.util.Vector;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.wizard.IWizardPage;
import org.eclipse.jface.wizard.Wizard;
import org.eclipse.rse.core.model.IHost;
import org.eclipse.rse.core.model.IPropertySet;
import org.eclipse.rse.ui.RSEUIPlugin;
import org.eclipse.rse.ui.actions.SystemRefreshAllAction;
import org.eclipse.tm.discovery.model.Pair;
import org.eclipse.tm.discovery.model.Service;
import org.eclipse.tm.discovery.model.ServiceType;
import org.eclipse.tm.discovery.wizard.ServiceDiscoveryWizardDisplayPage;
import org.eclipse.tm.discovery.wizard.ServiceDiscoveryWizardMainPage;

/**
 * Service Discovery Wizard
 */

public class ServiceDiscoveryWizard extends Wizard {
	private ServiceDiscoveryWizardMainPage serviceDiscoveryMainPage;

	private ServiceDiscoveryWizardDisplayPage serviceDiscoveryPage = null;

	/**
	 * Service Discovery Wizard constructor
	 */
	public ServiceDiscoveryWizard() {
		super();
		setNeedsProgressMonitor(false);

	}

	/* (non-Javadoc)
	 * @see org.eclipse.jface.wizard.Wizard#addPages()
	 */
	public void addPages() {

		serviceDiscoveryMainPage = new ServiceDiscoveryWizardMainPage();
		addPage(serviceDiscoveryMainPage);

	}

	/* (non-Javadoc)
	 * @see org.eclipse.jface.wizard.Wizard#getNextPage(org.eclipse.jface.wizard.IWizardPage)
	 */
	public IWizardPage getNextPage(IWizardPage page) {

		if (page instanceof ServiceDiscoveryWizardMainPage) {
			if (serviceDiscoveryPage == null) {
				serviceDiscoveryPage = new ServiceDiscoveryWizardDisplayPage(serviceDiscoveryMainPage.getQuery(), serviceDiscoveryMainPage.getAddress(), serviceDiscoveryMainPage.getTransport(), serviceDiscoveryMainPage.getProtocol(), serviceDiscoveryMainPage.getTimeOut());
				addPage(serviceDiscoveryPage);
			} else {
				serviceDiscoveryPage.update(serviceDiscoveryMainPage.getQuery(), serviceDiscoveryMainPage.getAddress(), serviceDiscoveryMainPage.getTransport(), serviceDiscoveryMainPage.getProtocol(), serviceDiscoveryMainPage.getTimeOut());
			}
		}
		return super.getNextPage(page);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jface.wizard.Wizard#performFinish()
	 */
	public boolean performFinish() {

		SystemRefreshAllAction systemRefreshAllAction = new SystemRefreshAllAction(null);
		
		String[] addresses = serviceDiscoveryPage.getAddresses();
		for (int i = 0; i < addresses.length; i++) {

			String hostName = addresses[i];
			Vector discoveredServices = serviceDiscoveryPage.getSelectedServices(addresses[i]);

			Enumeration serviceEnumeration = discoveredServices.elements();

			while (serviceEnumeration.hasMoreElements()) {
				IHost conn = null;

				Service service = (Service) serviceEnumeration.nextElement();
				String sysTypeString = ((ServiceType) service.eContainer()).getName();

				try {
					conn = RSEUIPlugin.getDefault().getSystemRegistry().createHost(sysTypeString, service.getName() + "@" + hostName, hostName, "Discovered "+sysTypeString+" server in "+hostName); //$NON-NLS-1$ //$NON-NLS-2$
				
					if (conn != null) {
						//copy discovered properties to RSE model
						IPropertySet ps = conn.getConnectorServices()[0].createPropertySet(Messages.ServiceDiscoveryWizard_DiscoveryPropertySet);
						Iterator pairIterator = service.getPair().iterator();
						while (pairIterator.hasNext()) {
							Pair pair = (Pair) pairIterator.next();
							ps.addProperty(pair.getKey(), pair.getValue());
	
							//add port to the RSE connection
							if (pair.getKey().equalsIgnoreCase(Messages.ServiceDiscoveryWizard_Port)) {
								conn.getConnectorServices()[0].setPort(Integer.parseInt(pair.getValue()));
							}
						}
						RSEUIPlugin.getDefault().getSystemRegistry().expandHost(conn);
					}
				} catch (Exception e) {
					if (conn != null) {
						RSEUIPlugin.getDefault().getSystemRegistry().deleteHost(conn);
					}
				} finally {
					systemRefreshAllAction.run();
				}
			}

			(new Job(Messages.ServiceDiscoveryWizard_SavingMessage) {

				protected IStatus run(IProgressMonitor monitor) {
					RSEUIPlugin.getDefault().getSystemRegistry().save();
					return new Status(IStatus.OK, Messages.ServiceDiscoveryWizard_StatusId, IStatus.OK, Messages.ServiceDiscoveryWizard_StatusMessage, null);
				}
			}).schedule(5000);

		}
		return true;
	}

}