/*
    Calimero 3 - A library for KNX network access
    Copyright (c) 2010, 2025 B. Malinowsky

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
import io.calimero.ReturnCode;
import io.calimero.datapoint.Datapoint;
import io.calimero.datapoint.DatapointMap;
import io.calimero.datapoint.StateDP;
import io.calimero.device.BaseKnxDevice;
import io.calimero.device.KnxDevice;
import io.calimero.device.KnxDeviceServiceLogic;
import io.calimero.device.LinkProcedure;
import io.calimero.device.ServiceResult;
import io.calimero.device.ios.DeviceObject;
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
import io.calimero.dptxlator.PropertyTypes;
import io.calimero.dptxlator.TranslatorTypes;
import io.calimero.internal.Executor;
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

	private static final int pidOperationMode = 52;

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
		final String s = TranslatorTypes.createTranslator(dp.dptId()).getValue();
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

		final InterfaceObjectServer ios = device.getInterfaceObjectServer();
		// the rest here just sets some arbitrary values in interface objects required for testing
		try {
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

			if (device.getAddress().equals(TestNetwork.programmableDevice)) {
				final int pidFeaturesSupported = 89;
				final var featureBits = new byte[10];
				// don't indicate extended memory services (bit 3), so core lib can test normal mem r/w
				featureBits[9] = (1 << 1) | (1 << 2) | (0 << 3) | (1 << 4) | (1 << 6);
				featureBits[8] = (1 << 0) | (1 << 2) | (1 << 3) | (1 << 4) | (1 << 6);
				final var deviceObject = DeviceObject.lookup(ios);
				deviceObject.set(pidFeaturesSupported, featureBits);
			}

			if (device.getDeviceLink().getKNXMedium() instanceof RFSettings) {
				final var rfObject = ios.addInterfaceObject(InterfaceObject.RF_MEDIUM_OBJECT);
				final int pidRfMultiType = 51;
				ios.setProperty(rfObject.getIndex(), pidRfMultiType, 1, 1, (byte) 0);
			}
		}
		catch (final RuntimeException e) {
			e.printStackTrace();
		}

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

		final var appProg = ios.lookup(InterfaceObject.APPLICATIONPROGRAM_OBJECT, 1);
		ios.setDescription(new Description(appProg.getIndex(), 0, pidOperationMode, 0,
				PropertyTypes.PDT_FUNCTION, true, 1, 1, 3, 3), true);
		ios.setProperty(InterfaceObject.APPLICATIONPROGRAM_OBJECT, 1, pidOperationMode, 1, 1, (byte) 0);
	}

	@Override
	public DPTXlator requestDatapointValue(final Datapoint dp) throws KNXException
	{
		if (!isResponder.contains(dp))
			return null;
		return TranslatorTypes.createTranslator(dp.dptId(), state.get(dp.getMainAddress()));
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
	public ServiceResult<byte[]> functionPropertyCommand(final Destination remote, final int objectIndex,
			final int propertyId, final byte[] command) {
		final var ios = device.getInterfaceObjectServer();
		final int objectType = ios.getInterfaceObjects()[objectIndex].getType();

		if (objectType == InterfaceObject.APPLICATIONPROGRAM_OBJECT && propertyId == pidOperationMode) {
			if (command.length > 2) {
				final int reserved = command[0] & 0xff;
				final int serviceId = command[1] & 0xff;
				if (reserved != 0 || serviceId != 0)
					return ServiceResult.of(ReturnCode.of(0xA0), (byte) serviceId, (byte) 0, (byte) 0xff);
				if (command.length == 3) {
					final int opMode = command[2] & 0xff;
					if (opMode > 1)
						return ServiceResult.of(ReturnCode.of(0xA0), (byte) serviceId, (byte) 0, (byte) 0xff);
					return new ServiceResult<>((byte) 0x20, (byte) serviceId, (byte) 0, (byte) 0xff);
				}
			}
		}
		return super.functionPropertyCommand(remote, objectIndex, propertyId, command);
	}

	@Override
	public ServiceResult<byte[]> readFunctionPropertyState(final Destination remote, final int objectIndex,
			final int propertyId, final byte[] functionInput) {
		final var ios = device.getInterfaceObjectServer();
		final int objectType = ios.getInterfaceObjects()[objectIndex].getType();

		if (objectType == InterfaceObject.APPLICATIONPROGRAM_OBJECT && propertyId == pidOperationMode) {
			if (functionInput.length > 1) {
				final int reserved = functionInput[0] & 0xff;
				final int serviceId = functionInput[1] & 0xff;
				if (reserved != 0 || serviceId != 0)
					return ServiceResult.of(ReturnCode.of(0xA0), (byte) serviceId, (byte) 0, (byte) 0xff);
				return ServiceResult.of(ReturnCode.of(0x20), (byte) serviceId, (byte) 0, (byte) 0xff);
			}
		}
		return super.readFunctionPropertyState(remote, objectIndex, propertyId, functionInput);
	}

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
}
