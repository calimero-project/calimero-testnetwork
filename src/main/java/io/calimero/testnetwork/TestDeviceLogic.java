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

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.lang.invoke.MethodHandles;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import io.calimero.GroupAddress;
import io.calimero.IndividualAddress;
import io.calimero.KNXAddress;
import io.calimero.KNXException;
import io.calimero.KNXFormatException;
import io.calimero.datapoint.Datapoint;
import io.calimero.datapoint.DatapointMap;
import io.calimero.datapoint.StateDP;
import io.calimero.device.BaseKnxDevice;
import io.calimero.device.KnxDevice;
import io.calimero.device.KnxDeviceServiceLogic;
import io.calimero.device.LinkProcedure;
import io.calimero.device.ServiceResult;
import io.calimero.device.ios.InterfaceObject;
import io.calimero.device.ios.InterfaceObjectServer;
import io.calimero.device.ios.KnxPropertyException;
import io.calimero.dptxlator.DPT;
import io.calimero.dptxlator.DPTXlator;
import io.calimero.dptxlator.DPTXlator2ByteFloat;
import io.calimero.dptxlator.DPTXlator2ByteUnsigned;
import io.calimero.dptxlator.DPTXlator3BitControlled;
import io.calimero.dptxlator.DPTXlator4ByteFloat;
import io.calimero.dptxlator.DPTXlator8BitUnsigned;
import io.calimero.dptxlator.DPTXlatorBoolean;
import io.calimero.dptxlator.DPTXlatorString;
import io.calimero.dptxlator.DptXlator16BitSet;
import io.calimero.dptxlator.TranslatorTypes;
import io.calimero.internal.Executor;
import io.calimero.knxnetip.KNXnetIPRouting;
import io.calimero.link.medium.RFSettings;
import io.calimero.log.LogService;
import io.calimero.mgmt.Description;
import io.calimero.mgmt.Destination;
import io.calimero.mgmt.ManagementClient.EraseCode;
import io.calimero.mgmt.ManagementClientImpl;
import io.calimero.mgmt.PropertyAccess;
import io.calimero.mgmt.PropertyAccess.PID;
import io.calimero.mgmt.TransportLayer;

/**
 * Test device logic for KNX devices in our test network.
 */
class TestDeviceLogic extends KnxDeviceServiceLogic
{
	private static final Logger logger = LogService.getLogger(MethodHandles.lookup().lookupClass());

	// PID.PROJECT_INSTALLATION_ID
	private static final int defProjectInstallationId = 0;
	// PID.ROUTING_MULTICAST_ADDRESS
	private static final InetAddress defRoutingMulticast = KNXnetIPRouting.DefaultMulticast;
	// PID.MAC_ADDRESS
	private static final byte[] defMacAddress;

	static {
		byte[] mac = null;
		try {
			mac = NetworkInterface.getByInetAddress(InetAddress.getLocalHost()).getHardwareAddress();
		}
		catch (SocketException | UnknownHostException | NullPointerException ignore) {}
		defMacAddress = mac != null ? mac : new byte[6];
	}

	// Values used for service families DIB

	// PID.KNXNETIP_DEVICE_CAPABILITIES
	// Bits LSB to MSB: 0 Device Management, 1 Tunneling, 2 Routing, 3 Remote Logging,
	// 4 Remote Configuration and Diagnosis, 5 Object Server
	private static final int defDeviceCaps = 1 + 2 + 4;

	// server friendly name, matches PID.FRIENDLY_NAME property
	private static final String friendlyName = "KNX Test Device";

	private final Map<GroupAddress, String> state = new HashMap<>();
	private final List<Datapoint> isResponder = new ArrayList<>();

	TestDeviceLogic() throws KNXException
	{
		addDatapoint("0/0/7", DPTXlatorBoolean.DPT_SWITCH);

		addDatapoint("0/1/0", "input trigger", DPTXlatorBoolean.DPT_BOOL);
		addDatapoint("0/1/1", "G1 switch", DPTXlatorBoolean.DPT_BOOL);
		addDatapoint("0/1/2", "G2 switch",  DPTXlatorBoolean.DPT_BOOL);
		addDatapoint("0/1/10", "switching input G2", DPTXlatorBoolean.DPT_BOOL);

		addDatapoint("1/0/1", "Bool", DPTXlatorBoolean.DPT_BOOL);
		addDatapoint("1/0/11", "Bool 2", DPTXlatorBoolean.DPT_ENABLE);
		addDatapoint("1/0/111", "Bool 3", DPTXlatorBoolean.DPT_OCCUPANCY);
		addDatapoint("1/0/2", DPTXlator3BitControlled.DPT_CONTROL_BLINDS);
		addDatapoint("1/0/3", DPTXlator8BitUnsigned.DPT_SCALING);
		addDatapoint("1/0/4", DPTXlator2ByteUnsigned.DPT_VALUE_2_UCOUNT);
		addDatapoint("1/0/5", DPTXlatorString.DPT_STRING_8859_1);
		state.put(new GroupAddress("1/0/5"), "Hello KNX!");
		addDatapoint("1/0/6", DPTXlator2ByteFloat.DPT_RAIN_AMOUNT);
		addDatapoint("1/0/7", DPTXlator4ByteFloat.DPT_ACCELERATION);

		addDatapoint("1/0/200", DPTXlator2ByteUnsigned.DPT_ABSOLUTE_COLOR_TEMPERATURE);
		addDatapoint("1/0/205", DptXlator16BitSet.DptRhccStatus);
		addDatapoint("1/0/206", DptXlator16BitSet.DptMedia);
	}

	private void addDatapoint(final String address, final DPT dpt) throws KNXException {
		addDatapoint(address, dpt.getDescription(), dpt);
	}

	private void addDatapoint(final String address, final String name, final DPT dpt) throws KNXException {
		final StateDP dp = new StateDP(new GroupAddress(address), name, 0, dpt.getID());
		getDatapointModel().add(dp);
		final String s = TranslatorTypes.createTranslator(0, dp.getDPT()).getValue();
		state.put(dp.getMainAddress(), s);
	}

	@Override
	public void setDevice(final KnxDevice device)
	{
		super.setDevice(device);

		for (int i = 0; i < 1000; i++)
			writeMemory(i, new byte[] { (byte) i });

		if (device.getAddress().equals(TestNetwork.programmableDevice))
			setProgrammingMode(true);
		if (device.getAddress().equals(TestNetwork.responderDevice))
			isResponder.addAll(((DatapointMap<Datapoint>) getDatapointModel()).getDatapoints());

		// the rest here just sets some arbitrary values in interface objects required for testing
		try {
			final InterfaceObjectServer ios = device.getInterfaceObjectServer();
			// set TP1 medium type
			ios.setDescription(new Description(0, 0, PID.MEDIUM_TYPE, 0, 0, false, 0, 1, 3, 0), true);
			ios.setProperty(0, PID.MEDIUM_TYPE, 1, 1, new byte[] { 0x1 });

			// set device control property, used for checking verify mode
			ios.setDescription(new Description(0, 0, PID.DEVICE_CONTROL, 0, 0, true, 0, 1, 3, 3), true);
			ios.setProperty(0, PID.DEVICE_CONTROL, 1, 1, new byte[] { 0x0 });

			final int last = device.getAddress().getDevice() + 1;
			final byte[] serialNo = new byte[] { 0x1, 0x2, 0x3, 0x4, 0x5, (byte) last };
			ios.setProperty(0, PID.SERIAL_NUMBER, 1, 1, serialNo);
			ios.setDescription(new Description(0, 0, PID.SERIAL_NUMBER, 0, 0, false, 0, 1, 3, 0), true);

			if (device.getDeviceLink().getKNXMedium() instanceof RFSettings) {
				final var rfObject = ios.addInterfaceObject(InterfaceObject.RF_MEDIUM_OBJECT);
				final int pidRfMultiType = 51;
				ios.setProperty(rfObject.getIndex(), pidRfMultiType, 1, 1, (byte) 0);
			}
		}
		catch (final RuntimeException e) {
			e.printStackTrace();
		}

		final InterfaceObjectServer ios = device.getInterfaceObjectServer();
		ios.addInterfaceObject(InterfaceObject.KNXNETIP_PARAMETER_OBJECT);

		// create interface object list for property PID_IO_LIST
		final InterfaceObject[] interfaceObjects = ios.getInterfaceObjects();
		final int iosize = interfaceObjects.length;
		final byte[] ioList = new byte[iosize * 2];
		for (final InterfaceObject io : interfaceObjects) {
			ioList[io.getIndex() * 2 + 1] = (byte) io.getType();
		}
		try {
			ios.setProperty(0, 1, PID.IO_LIST, 1, iosize, ioList);
		}
		catch (final KnxPropertyException e1) {
			e1.printStackTrace();
		}

		int idx = 2;
		setProgramData(ios, idx, (byte) 3);
		idx = 5;
		setProgramData(ios, idx, (byte) 5);
		idx = 3;
		try {
			ios.setProperty(idx, PropertyAccess.PID.LOAD_STATE_CONTROL, 1, 1, (byte) 1);
			idx = 4;
			ios.setProperty(idx, PropertyAccess.PID.LOAD_STATE_CONTROL, 1, 1, (byte) 4);
		}
		catch (final KnxPropertyException e) {
			e.printStackTrace();
		}

		try {
			initKNXnetIpParameterObject(ios, 1);
		}
		catch (final KnxPropertyException e) {
			e.printStackTrace();
		}
	}

	@Override
	public DPTXlator requestDatapointValue(final Datapoint dp) throws KNXException
	{
		if (!isResponder.contains(dp))
			return null;
		final DPTXlator t = TranslatorTypes.createTranslator(0, dp.getDPT());
		t.setValue(state.get(dp.getMainAddress()));
		return t;
	}

	@Override
	public void updateDatapointValue(final Datapoint ofDp, final DPTXlator update)
	{
		state.put(ofDp.getMainAddress(), update.getValue());
	}

	@Override
	public ServiceResult<byte[]> readParameter(final int objectType, final int pid, final byte[] info) {
		if (objectType != 0 || pid != 59)
			return super.readParameter(objectType, pid, info);

		final boolean broadcast = false; // dst.equals(GroupAddress.Broadcast); // XXX
		final byte[] response = new byte[1];
		response[0] = 0xa;
		final int tmedium = device.getDeviceLink().getKNXMedium().timeFactor();
		final int wait = broadcast ? new Random().nextInt(10 * tmedium) : 0;
		logger.log(Level.DEBUG, "add random wait time of " + wait + " ms before response");
		try {
			Thread.sleep(wait);
		}
		catch (final InterruptedException e) {
			e.printStackTrace();
		}
		return ServiceResult.of(response);
	}

	@Override
	public void writeParameter(final int objectType, final int pid, final byte[] info) {
		if (LinkProcedure.isEnterConfigMode(objectType, pid, info)) {
			final ManagementClientImpl mgmt = new ManagementClientImpl(device.getDeviceLink(),
					((BaseKnxDevice) device).transportLayer()) {};
			final Map<Integer, GroupAddress> groupObjects = new HashMap<>();
			final int CC_Switch_OnOff = 1;
			final int CC_Dimming_Ctrl = 5;
			groupObjects.put(CC_Switch_OnOff, new GroupAddress(7, 3, 10));
			groupObjects.put(CC_Dimming_Ctrl, new GroupAddress(7, 3, 11));

			final var respondTo = mgmt.createDestination(new IndividualAddress(1), false);
			final var linkProc = LinkProcedure.forSensor(mgmt, device.getAddress(), respondTo, false, 0xbeef,
					groupObjects);
			linkProc.setLinkFunction(this::onLinkResponse);
			Executor.execute(linkProc, device.getAddress() + " Link Procedure Thread");
		}
	}

	@Override
	public ServiceResult<Integer> readADC(final int channel, final int consecutiveReads)
	{
		return ServiceResult.of(0x100);
	}

	@Override
	public ServiceResult<Integer> authorize(final Destination remote, final byte[] key)
	{
		final byte[] validKey = new byte[] { 0x10, 0x20, 0x30, 0x40 };
		final int levelValid = 2;

		if (Arrays.equals(key, validKey)) {
			final int currentLevel = levelValid;
			return ServiceResult.of(currentLevel);
		}
		return super.authorize(remote, key);
	}

	@Override
	public ServiceResult<Duration> restart(final boolean masterReset, final EraseCode eraseCode, final int channel)
	{
		final var result = super.restart(masterReset, eraseCode, channel);
		if (device.getAddress().equals(new IndividualAddress(1, 1, 4)))
			setProgrammingMode(true);
		return result;
	}

	private static final int NetworkParameterRes = 0b1111011011;
	private static final int SystemNetworkParamResponse = 0b0111001001;

	@Override
	public ServiceResult<byte[]> management(final int svcType, final byte[] asdu, final KNXAddress dst,
		final Destination respondTo, final TransportLayer tl)
	{
		if (svcType == NetworkParameterRes || svcType == SystemNetworkParamResponse)
			return null;
		return super.management(svcType, asdu, dst, respondTo, tl);
	}

	private int onLinkResponse(final int flags, final Map<Integer, GroupAddress> groupObjects)
	{
		logger.log(Level.INFO, "link response: flags " + flags + " and group objects " + groupObjects);
		return 0;
	}

	// precondition: we have an IOS instance
	private static void initKNXnetIpParameterObject(final InterfaceObjectServer ios, final int objectInstance)
		throws KnxPropertyException
	{
		final int knxObject = 11;
		// reset transmit counter to 0
		// those two are 4 byte unsigned
		ios.setProperty(knxObject, objectInstance, PID.MSG_TRANSMIT_TO_IP, 1, 1, new byte[4]);
		ios.setProperty(knxObject, objectInstance, PID.MSG_TRANSMIT_TO_KNX, 1, 1, new byte[4]);

		//
		// set properties used in device DIB for search response during discovery
		//
		// friendly name property entry is an array of 30 characters
		final byte[] data = new byte[30];
		System.arraycopy(friendlyName.getBytes(StandardCharsets.ISO_8859_1), 0, data, 0, friendlyName.length());
		ios.setProperty(knxObject, objectInstance, PID.FRIENDLY_NAME, 1, data.length, data);
		ios.setProperty(knxObject, objectInstance, PID.PROJECT_INSTALLATION_ID, 1, 1,
				bytesFromWord(defProjectInstallationId));
		// server KNX device address, since we don't know about routing at this time
		// address is always 0.0.0, but is updated in setRoutingConfiguration
		final byte[] device = new IndividualAddress(0).toByteArray();
		ios.setProperty(knxObject, objectInstance, PID.KNX_INDIVIDUAL_ADDRESS, 1, 1, device);
		ios.setProperty(knxObject, objectInstance, PID.MAC_ADDRESS, 1, 1, defMacAddress);

		// routing stuff
		resetRoutingConfiguration(ios);

		// ip and setup multicast
		byte[] ip = new byte[4];
		try {
			ip = InetAddress.getLocalHost().getAddress();
		}
		catch (final UnknownHostException e) {}
		ios.setProperty(knxObject, objectInstance, PID.CURRENT_IP_ADDRESS, 1, 1, ip);
		ip[3] = (byte) (ip[3] - 1);
		ios.setProperty(knxObject, objectInstance, PID.IP_ADDRESS, 1, 1, ip);
		ios.setProperty(knxObject, objectInstance, PID.SUBNET_MASK, 1, 1, new byte[] { -1, -1, -1, 0 });
		ios.setProperty(knxObject, objectInstance, PID.DEFAULT_GATEWAY, 1, 1, ip);
		ios.setProperty(knxObject, objectInstance, PID.TTL, 1, 1, new byte[] { 9 });

		try {
			final byte[] a1 = new IndividualAddress("1.1.5").toByteArray();
			final byte[] a2 = new IndividualAddress("1.1.6").toByteArray();
			final byte[] a3 = new IndividualAddress("1.1.7").toByteArray();
			final int idx = ios.getProperty(knxObject, 1, PID.OBJECT_INDEX, 1, 1)[0];
			ios.setDescription(new Description(idx, knxObject, PID.ADDITIONAL_INDIVIDUAL_ADDRESSES, 0, 0, true, 0, 10, 3, 3), true);
			ios.setProperty(knxObject, objectInstance, PID.ADDITIONAL_INDIVIDUAL_ADDRESSES, 1, 1, a1);
			ios.setProperty(knxObject, objectInstance, PID.ADDITIONAL_INDIVIDUAL_ADDRESSES, 2, 1, a2);
			ios.setProperty(knxObject, objectInstance, PID.ADDITIONAL_INDIVIDUAL_ADDRESSES, 3, 1, a3);
		}
		catch (final KNXFormatException e) {
			e.printStackTrace();
		}

		ios.setProperty(knxObject, objectInstance, PID.SYSTEM_SETUP_MULTICAST_ADDRESS, 1, 1,
				defRoutingMulticast.getAddress());

		//
		// set properties used in service families DIB for description
		//
		ios.setProperty(knxObject, objectInstance, PID.KNXNETIP_DEVICE_CAPABILITIES, 1, 1,
				bytesFromWord(defDeviceCaps));

		//
		// set properties used in manufacturer data DIB for discovery self-description
		//
		final byte[] zero = new byte[1];
		// we don't indicate any capabilities here, since executing the respective tasks
		// is either done in the gateway (and, therefore, the property is set by the
		// gateway) or by the user, who has to care about it on its own
		ios.setProperty(knxObject, objectInstance, PID.KNXNETIP_ROUTING_CAPABILITIES, 1, 1, zero);
		ios.setProperty(knxObject, objectInstance, PID.KNXNETIP_DEVICE_STATE, 1, 1, zero);

		ios.setProperty(knxObject, objectInstance, PID.IP_CAPABILITIES, 1, 1, zero);
		ios.setProperty(knxObject, objectInstance, PID.IP_ASSIGNMENT_METHOD, 1, 1, new byte[] { 1 });
		ios.setProperty(knxObject, objectInstance, PID.CURRENT_IP_ASSIGNMENT_METHOD, 1, 1, new byte[] { 1 });
	}

	private static void resetRoutingConfiguration(final InterfaceObjectServer ios)
	{
		// routing multicast shall be set 0 if no routing service offered
		try {
			ios.setProperty(InterfaceObject.KNXNETIP_PARAMETER_OBJECT, 1, PID.ROUTING_MULTICAST_ADDRESS, 1, 1,
					new byte[4]);
		}
		catch (final KnxPropertyException e) {}
	}

	private static void setProgramData(final InterfaceObjectServer ios, final int idx, final byte value)
	{
		try {
			ios.setProperty(idx, PropertyAccess.PID.PROGRAM_VERSION, 1, 1, value, value, value, value, value);
			ios.setProperty(idx, PropertyAccess.PID.LOAD_STATE_CONTROL, 1, 1, value);
			ios.setProperty(idx, PropertyAccess.PID.RUN_STATE_CONTROL, 1, 1, value);
			ios.setProperty(idx, PropertyAccess.PID.ERROR_CODE, 1, 1, new byte[] { 8 });
		}
		catch (final KnxPropertyException e) {
			e.printStackTrace();
		}
	}

	private static byte[] bytesFromWord(final int word)
	{
		return new byte[] { (byte) (word >> 8), (byte) word };
	}
}
