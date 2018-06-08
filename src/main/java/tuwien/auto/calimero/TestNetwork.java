/*
    Calimero 2 - A library for KNX network access
    Copyright (c) 2010, 2018 B. Malinowsky

    This program is free software; you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation; either version 2 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program; if not, write to the Free Software
    Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA

    Linking this library statically or dynamically with other modules is
    making a combined work based on this library. Thus, the terms and
    conditions of the GNU General Public License cover the whole
    combination.

    As a special exception, the copyright holders of this library give you
    permission to link this library with independent modules to produce an
    executable, regardless of the license terms of these independent
    modules, and to copy and distribute the resulting executable under terms
    of your choice, provided that you also meet, for each linked independent
    module, the terms and conditions of the license of that module. An
    independent module is a module which is not derived from or based on
    this library. If you modify this library, you may extend this exception
    to your version of the library, but you are not obligated to do so. If
    you do not wish to do so, delete this exception statement from your
    version.
*/

package tuwien.auto.calimero;

import java.util.List;

import tuwien.auto.calimero.device.BaseKnxDevice;
import tuwien.auto.calimero.device.KnxDevice;
import tuwien.auto.calimero.link.KNXNetworkLink;
import tuwien.auto.calimero.process.ProcessCommunicationBase;
import tuwien.auto.calimero.process.ProcessCommunicator;
import tuwien.auto.calimero.process.ProcessCommunicatorImpl;
import tuwien.auto.calimero.server.Launcher;
import tuwien.auto.calimero.server.VirtualLink;
import tuwien.auto.calimero.server.gateway.KnxServerGateway;
import tuwien.auto.calimero.server.gateway.SubnetConnector;

public class TestNetwork implements Runnable
{
	// loop interval [ms] of issuing read/write datapoint requests to keep a "live" network
	private static final int UpdateInterval = 10000;

	private final String configURI;

	/**
	 * @param args server config URI
	 */
	public static void main(final String[] args)
	{
		if (args.length == 0)
			System.err.println("supply calimero-server configuration -- exit");
		else
			new TestNetwork(args).run();
	}

	public TestNetwork(final String[] args)
	{
		configURI = args[0];
	}

	@Override
	public void run()
	{
		System.getProperties().setProperty("org.slf4j.simpleLogger.logFile", "System.out");
		System.getProperties().setProperty("org.slf4j.simpleLogger.showLogName", "true");

		try {
			final Launcher launcher = new Launcher(configURI);
			new Thread(launcher).start();
			Thread.sleep(1000);

			final KnxServerGateway gw = launcher.getGateway();
			if (gw == null) {
				System.err.println("Gateway not started - exit");
				launcher.quit();
				return;
			}
			final List<SubnetConnector> connectors = gw.getSubnetConnectors();
			final VirtualLink link = (VirtualLink) connectors.get(0).getSubnetLink();

			final KnxDevice d4 = createDevice("1.1.4", link);
			/*final KnxDevice d5 =*/ createDevice("1.1.5", link);
			System.out.println("Test network is up and running");

			boolean state = true;
			int intState = 13;
			try (ProcessCommunicator pc = new ProcessCommunicatorImpl(d4.getDeviceLink())) {
				while (true) {
					Thread.sleep(UpdateInterval);
					final boolean createReadWriteTraffic = true;
					if (createReadWriteTraffic) {
//						try {
//							state = !state;
//							pc.write(new GroupAddress("1/0/1"), state);
//							pc.readBool(new GroupAddress("1/0/1"));
//						}
//						catch (final KNXException e) {
//							System.out.println(e);
//						}

						try {
							intState = ++intState % 101;
							pc.write(new GroupAddress("1/0/3"), intState, ProcessCommunicationBase.SCALING);
							pc.readUnsigned(new GroupAddress("1/0/3"), ProcessCommunicationBase.SCALING);
						}
						catch (final KNXException e) {
							System.out.println(e);
						}
					}
				}
			}
		}
		catch (KNXException | InterruptedException e) {
			e.printStackTrace();
		}
	}

	private static KnxDevice createDevice(final String address, final VirtualLink downLink) throws KNXException
	{
		final IndividualAddress ia = new IndividualAddress(address);
		final TestDeviceLogic logic = new TestDeviceLogic();
		final KNXNetworkLink devLink = downLink.createDeviceLink(ia);
		final KnxDevice dev = new BaseKnxDevice("Device " + ia.toString(), logic, devLink);
		// we have to init device stuff in setDevice, because before the device is null
//		logic.setDevice(dev);
		return dev;
	}
}
