/*
    Calimero 2 - A library for KNX network access
    Copyright (c) 2010, 2023 B. Malinowsky

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

package io.calimero.testnetwork;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.time.Duration;
import java.util.List;

import io.calimero.DataUnitBuilder;
import io.calimero.DeviceDescriptor;
import io.calimero.GroupAddress;
import io.calimero.IndividualAddress;
import io.calimero.KNXException;
import io.calimero.KNXTimeoutException;
import io.calimero.Priority;
import io.calimero.SerialNumber;
import io.calimero.device.BaseKnxDevice;
import io.calimero.device.KnxDevice;
import io.calimero.device.ios.InterfaceObject;
import io.calimero.link.KNXNetworkLink;
import io.calimero.mgmt.ManagementClient;
import io.calimero.mgmt.ManagementClientImpl;
import io.calimero.mgmt.PropertyAccess.PID;
import io.calimero.process.ProcessCommunication;
import io.calimero.process.ProcessCommunicator;
import io.calimero.process.ProcessCommunicatorImpl;
import io.calimero.server.Launcher;
import io.calimero.server.VirtualLink;
import io.calimero.server.gateway.KnxServerGateway;
import io.calimero.server.gateway.SubnetConnector;

/**
 * The test network setup.
 */
public class TestNetwork implements Runnable
{
	// loop interval [ms] of issuing read/write datapoint requests to keep a "live" network
	private static final int UpdateInterval = 10000;

	static final IndividualAddress programmableDevice = new IndividualAddress(1, 1, 4);
	static final IndividualAddress responderDevice = new IndividualAddress(1, 1, 5);

	private final String configURI;

	/**
	 * Main entry-point.
	 *
	 * @param args server config URI
	 */
	public static void main(final String[] args)
	{
		if (args.length == 0)
			System.err.println("supply calimero-server configuration -- exit");
		else
			new TestNetwork(args).run();
	}

	/**
	 * Creates a new instance of the test network.
	 *
	 * @param args server config URI
	 */
	public TestNetwork(final String[] args)
	{
		configURI = args[0];
	}

	@Override
	public void run()
	{
		System.getProperties().setProperty("org.slf4j.simpleLogger.logFile", "System.out");
		System.getProperties().setProperty("org.slf4j.simpleLogger.showLogName", "true");

		final String netif = System.getProperty("calimero.testnetwork.netif");
		if (netif != null) {
			// using RandomAccessFile because Files.readAllBytes(path) doesn't resolve 'server-config.xml' to cwd
			try (RandomAccessFile file = new RandomAccessFile(configURI, "r")) {
				final byte[] buf = new byte[(int) file.length()];
				file.readFully(buf);
				final String s = new String(buf, UTF_8).replace("listenNetIf=\"any\"", "listenNetIf=\"" + netif + "\"");
				try (FileOutputStream fos = new FileOutputStream(configURI, false)) {
					fos.write(s.getBytes(UTF_8));
				}
			}
			catch (final IOException e) {
				e.printStackTrace();
			}
		}

		try (var launcher = new Launcher(configURI)) {
			final Thread t = new Thread(launcher);
			t.start();
			Thread.sleep(1000);

			final KnxServerGateway gw = launcher.getGateway();
			if (gw == null) {
				System.err.println("Gateway not started - exit");
				return;
			}
			final var ios = gw.getServer().getInterfaceObjectServer();
			final List<SubnetConnector> connectors = gw.getSubnetConnectors();
			final VirtualLink link = (VirtualLink) connectors.get(0).getSubnetLink();

			final KnxDevice d4 = createDevice(programmableDevice, link);
			/*final KnxDevice d5 =*/ createDevice(responderDevice, link);

			routerObjectIndex = ios.lookup(InterfaceObject.ROUTER_OBJECT, 1).getIndex();

			// set baos properties for mode switch testing
			final int pidBaosSupport = 201;
			ios.setProperty(8, 1, pidBaosSupport, 1, 1, (byte) 1);
			final int pidCommMode = 52; // PID.COMM_MODE
			ios.setProperty(8, 1, pidCommMode, 1, 1, (byte) 0);



			System.out.println("Test network is up and running");

			boolean state = true;
			int intState = 13;
			try (ProcessCommunicator pc = new ProcessCommunicatorImpl(d4.getDeviceLink())) {
				while (true) {
					final String s = readStdin(UpdateInterval);
					if ("exit".equals(s))
						break;
					if ("stat".equals(s))
						System.out.println(gw);

					final boolean createReadWriteTraffic = true;
					if (createReadWriteTraffic) {
						try {
							state = !state;
							pc.write(new GroupAddress("1/0/1"), state);
							pc.readBool(new GroupAddress("1/0/1"));
						}
						catch (final KNXException e) {
							System.out.println(e);
						}
						try {
							intState = ++intState % 101;
							pc.write(new GroupAddress("1/0/3"), intState, ProcessCommunication.SCALING);
							pc.readUnsigned(new GroupAddress("1/0/3"), ProcessCommunication.SCALING);
						}
						catch (final KNXException e) {
							System.out.println(e);
						}
					}
					final boolean createSystemBroadcasts = true;
					if (createSystemBroadcasts) {
						sendSystemBroadcasts((BaseKnxDevice) d4);
					}
				}
			}
		}
		catch (KNXException | InterruptedException | IOException e) {
			e.printStackTrace();
		}
	}

	private static String readStdin(final int timeout) throws InterruptedException, IOException {
		final long now = System.nanoTime();
		final long end = now + timeout * 1_000_000L;

		final var reader = new BufferedReader(new InputStreamReader(System.in));
		String line;
		do {
			while (!reader.ready()) {
				if (System.nanoTime() > end)
					return "";
				Thread.sleep(200);
			}
			line = reader.readLine();
		}
		while ("".equals(line));
		return line;
	}

	private static final int A_FunctionPropertyCommand = 0b1011000111;
	private static final int pidIpSbcControl = 120;

	private int routerObjectIndex;

	private void sendSystemBroadcasts(final BaseKnxDevice device) throws KNXException, InterruptedException {
		try (ManagementClient mgmt = new ManagementClientImpl(device.getDeviceLink(), device.transportLayer()) {}) {
			// enable server system broadcast mode
			final var tsdu = DataUnitBuilder.createAPDU(A_FunctionPropertyCommand, (byte) routerObjectIndex, (byte) pidIpSbcControl,
					(byte) 0, (byte) 0, (byte) 1);
			device.transportLayer().sendData(new IndividualAddress(1, 1, 0), Priority.LOW, tsdu);

//			final byte[] sno = { 1, 2, 3, 4, 5, 6 };
//			final byte[] domain = { (byte) 224, 0, 23, 23 };
//			mgmt.writeDomainAddress(sno, domain);
			// this should be forwarded normally (not as sysbcast)
//			mgmt.writeDomainAddress(sno, new byte[] { 1, 2 });

			mgmt.responseTimeout(Duration.ofSeconds(1));
			final byte operand = 1;
			try {
				mgmt.readSystemNetworkParameter(0, PID.SERIAL_NUMBER, operand);
			}
			catch (final KNXTimeoutException ignore) {}

			// the following ones should be normally forwarded (not as sysbcast) from subnet -> IP
			try {
				mgmt.readSystemNetworkParameter(1, PID.SERIAL_NUMBER, operand);
			}
			catch (final KNXTimeoutException ignore) {}
			try {
				mgmt.readSystemNetworkParameter(0, 40, operand);
			}
			catch (final KNXTimeoutException ignore) {}
			try {
				mgmt.readSystemNetworkParameter(0, PID.SERIAL_NUMBER, (byte) 0);
			}
			catch (final KNXTimeoutException ignore) {}
		}
		finally {
			// disable server system broadcast mode
			final var tsdu = DataUnitBuilder.createAPDU(A_FunctionPropertyCommand, (byte) routerObjectIndex,
					(byte) pidIpSbcControl, (byte) 0, (byte) 0, (byte) 0);
			device.transportLayer().sendData(new IndividualAddress(1, 1, 0), Priority.LOW, tsdu);
		}
	}

	private static KnxDevice createDevice(final IndividualAddress address, final VirtualLink downLink) throws KNXException
	{
		final TestDeviceLogic logic = new TestDeviceLogic();
		final KNXNetworkLink devLink = downLink.createDeviceLink(address);
		final var dev = new BaseKnxDevice("Device-" + address.getDevice(), logic, devLink);
		final int last = address.getDevice() + 1;
		final var serialNo = SerialNumber.from(new byte[] { 0x1, 0x2, 0x3, 0x4, 0x5, (byte) last });
		final byte[] hardwareType = DataUnitBuilder.fromHex("00000000021A");
		dev.identification(DeviceDescriptor.DD0.TYPE_2705, 0x83, serialNo, hardwareType, new byte[5], new byte[16]);
		return dev;
	}
}
