/*
 * Title: CloudSim Toolkit Description: CloudSim (Cloud Simulation) Toolkit for Modeling and
 * Simulation of Clouds Licence: GPL - http://www.gnu.org/copyleft/gpl.html
 * 
 * Copyright (c) 2009-2012, The University of Melbourne, Australia
 */

package org.cloudbus.cloudsim;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.CloudSimTags;
import org.cloudbus.cloudsim.core.SimEntity;
import org.cloudbus.cloudsim.core.SimEvent;

/**
 * Datacenter class is a CloudResource whose hostList are virtualized. It deals with processing of
 * VM queries (i.e., handling of VMs) instead of processing Cloudlet-related queries. So, even
 * though an AllocPolicy will be instantiated (in the init() method of the superclass, it will not
 * be used, as processing of cloudlets are handled by the CloudletScheduler and processing of
 * VirtualMachines are handled by the VmAllocationPolicy.
 * 
 * This work has been modified by the DesktopCloudSim Group
 * 
 * @author Abdulelah Alwabel
 * @since DesktopCloudSim Toolkit 1.0
 */
public class Datacenter extends SimEntity {

	/** The characteristics. */
	private DatacenterCharacteristics characteristics;

	/** The regional cis name. */
	private String regionalCisName;

	/** The vm provisioner. */
	private VmAllocationPolicy vmAllocationPolicy;

	/** The last process time. */
	private double lastProcessTime;

	/** The storage list. */
	private List<Storage> storageList;

	/** The vm list. */
	private List<? extends Vm> vmList;

	/** The scheduling interval. */
	private double schedulingInterval;
	
	
	//private double failureEvent;
	private ArrayList <Host> failedHostList;

	/**
	 * Allocates a new PowerDatacenter object.
	 * 
	 * @param name the name to be associated with this entity (as required by Sim_entity class from
	 *            simjava package)
	 * @param characteristics an object of DatacenterCharacteristics
	 * @param storageList a LinkedList of storage elements, for data simulation
	 * @param vmAllocationPolicy the vmAllocationPolicy
	 * @throws Exception This happens when one of the following scenarios occur:
	 *             <ul>
	 *             <li>creating this entity before initializing CloudSim package
	 *             <li>this entity name is <tt>null</tt> or empty
	 *             <li>this entity has <tt>zero</tt> number of PEs (Processing Elements). <br>
	 *             No PEs mean the Cloudlets can't be processed. A CloudResource must contain one or
	 *             more Machines. A Machine must contain one or more PEs.
	 *             </ul>
	 * @pre name != null
	 * @pre resource != null
	 * @post $none
	 */
	public Datacenter(
			String name,
			DatacenterCharacteristics characteristics,
			VmAllocationPolicy vmAllocationPolicy,
			List<Storage> storageList,
			double schedulingInterval) throws Exception {
		super(name);

		setCharacteristics(characteristics);
		setVmAllocationPolicy(vmAllocationPolicy);
		setLastProcessTime(0.0);
		setStorageList(storageList);
		setVmList(new ArrayList<Vm>());
		setSchedulingInterval(schedulingInterval);

		
		//scheduling failures events can be done buffer 
		for (Host host : getCharacteristics().getHostList()) {
			host.setDatacenter(this);
		}

		// If this resource doesn't have any PEs then no useful at all
		if (getCharacteristics().getNumberOfPes() == 0) {
			throw new Exception(super.getName()
					+ " : Error - this entity has no PEs. Therefore, can't process any Cloudlets.");
		}

		// stores id of this class
		getCharacteristics().setId(super.getId());
		
		//failureEvent = 0;
		failedHostList = new ArrayList <Host>(); 
	}
	

	/**
	 * Overrides this method when making a new and different type of resource. <br>
	 * <b>NOTE:</b> You do not need to override {@link #body()} method, if you use this method.
	 * 
	 * @pre $none
	 * @post $none
	 */
	protected void registerOtherEntity() {
		// empty. This should be override by a child class
	}

	/**
	 * Processes events or services that are available for this PowerDatacenter.
	 * 
	 * @param ev a Sim_event object
	 * @pre ev != null
	 * @post $none
	 */
	
	@Override
	public void processEvent(SimEvent ev) {
		int srcId = -1;

		//printEvents(ev);
		
		switch (ev.getTag()) {
		// Resource characteristics inquiry
			case CloudSimTags.RESOURCE_CHARACTERISTICS:
				srcId = ((Integer) ev.getData()).intValue();
				sendNow(srcId, ev.getTag(), getCharacteristics());
				break;

			// Resource dynamic info inquiry
			case CloudSimTags.RESOURCE_DYNAMICS:
				srcId = ((Integer) ev.getData()).intValue();
				sendNow(srcId, ev.getTag(), 0);
				break;

			case CloudSimTags.RESOURCE_NUM_PE:
				srcId = ((Integer) ev.getData()).intValue();
				int numPE = getCharacteristics().getNumberOfPes();
				sendNow(srcId, ev.getTag(), numPE);
				break;

			case CloudSimTags.RESOURCE_NUM_FREE_PE:
				srcId = ((Integer) ev.getData()).intValue();
				int freePesNumber = getCharacteristics().getNumberOfFreePes();
				sendNow(srcId, ev.getTag(), freePesNumber);
				break;

			// New Cloudlet arrives
			case CloudSimTags.CLOUDLET_SUBMIT:
				processCloudletSubmit(ev, false);
				break;

			// New Cloudlet arrives, but the sender asks for an ack
			case CloudSimTags.CLOUDLET_SUBMIT_ACK:
				processCloudletSubmit(ev, true);
				break;

			// Cancels a previously submitted Cloudlet
			case CloudSimTags.CLOUDLET_CANCEL:
				processCloudlet(ev, CloudSimTags.CLOUDLET_CANCEL);
				break;

			// Pauses a previously submitted Cloudlet
			case CloudSimTags.CLOUDLET_PAUSE:
				processCloudlet(ev, CloudSimTags.CLOUDLET_PAUSE);
				break;

			// Pauses a previously submitted Cloudlet, but the sender
			// asks for an acknowledgement
			case CloudSimTags.CLOUDLET_PAUSE_ACK:
				processCloudlet(ev, CloudSimTags.CLOUDLET_PAUSE_ACK);
				break;

			// Resumes a previously submitted Cloudlet
			case CloudSimTags.CLOUDLET_RESUME:
				processCloudlet(ev, CloudSimTags.CLOUDLET_RESUME);
				break;

			// Resumes a previously submitted Cloudlet, but the sender
			// asks for an acknowledgement
			case CloudSimTags.CLOUDLET_RESUME_ACK:
				processCloudlet(ev, CloudSimTags.CLOUDLET_RESUME_ACK);
				break;

			// Moves a previously submitted Cloudlet to a different resource
			case CloudSimTags.CLOUDLET_MOVE:
				processCloudletMove((int[]) ev.getData(), CloudSimTags.CLOUDLET_MOVE);
				break;

			// Moves a previously submitted Cloudlet to a different resource
			case CloudSimTags.CLOUDLET_MOVE_ACK:
				processCloudletMove((int[]) ev.getData(), CloudSimTags.CLOUDLET_MOVE_ACK);
				break;

			// Checks the status of a Cloudlet
			case CloudSimTags.CLOUDLET_STATUS:
				processCloudletStatus(ev);
				break;

			// Ping packet
			case CloudSimTags.INFOPKT_SUBMIT:
				processPingRequest(ev);
				break;

			case CloudSimTags.VM_CREATE:
				processVmCreate(ev, false);
				break;

			case CloudSimTags.VM_CREATE_ACK:
				processVmCreate(ev, true);
				break;

			case CloudSimTags.VM_DESTROY:
				processVmDestroy(ev, false);
				break;

			case CloudSimTags.VM_DESTROY_ACK:
				processVmDestroy(ev, true);
				break;

			case CloudSimTags.VM_MIGRATE:
				processVmMigrate(ev, false);
				break;

			case CloudSimTags.VM_MIGRATE_ACK:
				processVmMigrate(ev, true);
				break;

			case CloudSimTags.VM_DATA_ADD:
				processDataAdd(ev, false);
				break;

			case CloudSimTags.VM_DATA_ADD_ACK:
				processDataAdd(ev, true);
				break;

			case CloudSimTags.VM_DATA_DEL:
				processDataDelete(ev, false);
				break;

			case CloudSimTags.VM_DATA_DEL_ACK:
				processDataDelete(ev, true);
				break;

			case CloudSimTags.VM_DATACENTER_EVENT:
				updateCloudletProcessing();
				checkCloudletCompletion();
				break;
				
			case CloudSimTags.HOST_FAIL:
				processHostFailure(ev);
				break;
								  
				//note: has to be deleted
			case CloudSimTags.HOST_REMOVE:
				processHostRemoval(ev);
				break;
							  
			case CloudSimTags.HOST_ALIVE:
				processHostJoining(ev);
				break;
			  
			case CloudSimTags.PRINT_DETAILS:
				printDetails();
				break;

			case CloudSimTags.VM_REPLICATE:
				processVmReplicate(ev);
				break;		
				
			case CloudSimTags.VM_DESTROY_ALL:
				processVmDestroyALL();
				break;		
				
			// other unknown tags are processed by this method
			default:
				processOtherEvent(ev);
				break;
		}
	}

	/**
	 * Process data del.
	 * 
	 * @param ev the ev
	 * @param ack the ack
	 */
	protected void processDataDelete(SimEvent ev, boolean ack) {
		if (ev == null) {
			return;
		}

		Object[] data = (Object[]) ev.getData();
		if (data == null) {
			return;
		}

		String filename = (String) data[0];
		int req_source = ((Integer) data[1]).intValue();
		int tag = -1;

		// check if this file can be deleted (do not delete is right now)
		int msg = deleteFileFromStorage(filename);
		if (msg == DataCloudTags.FILE_DELETE_SUCCESSFUL) {
			tag = DataCloudTags.CTLG_DELETE_MASTER;
		} else { // if an error occured, notify user
			tag = DataCloudTags.FILE_DELETE_MASTER_RESULT;
		}

		if (ack) {
			// send back to sender
			Object pack[] = new Object[2];
			pack[0] = filename;
			pack[1] = Integer.valueOf(msg);

			sendNow(req_source, tag, pack);
		}
	}

	/**
	 * Process data add.
	 * 
	 * @param ev the ev
	 * @param ack the ack
	 */
	protected void processDataAdd(SimEvent ev, boolean ack) {
		if (ev == null) {
			return;
		}

		Object[] pack = (Object[]) ev.getData();
		if (pack == null) {
			return;
		}

		File file = (File) pack[0]; // get the file
		file.setMasterCopy(true); // set the file into a master copy
		int sentFrom = ((Integer) pack[1]).intValue(); // get sender ID

		/******
		 * // DEBUG Log.printLine(super.get_name() + ".addMasterFile(): " + file.getName() +
		 * " from " + CloudSim.getEntityName(sentFrom));
		 *******/

		Object[] data = new Object[3];
		data[0] = file.getName();

		int msg = addFile(file); // add the file

		if (ack) {
			data[1] = Integer.valueOf(-1); // no sender id
			data[2] = Integer.valueOf(msg); // the result of adding a master file
			sendNow(sentFrom, DataCloudTags.FILE_ADD_MASTER_RESULT, data);
		}
	}

	/**
	 * Processes a ping request.
	 * 
	 * @param ev a Sim_event object
	 * @pre ev != null
	 * @post $none
	 */
	protected void processPingRequest(SimEvent ev) {
		InfoPacket pkt = (InfoPacket) ev.getData();
		pkt.setTag(CloudSimTags.INFOPKT_RETURN);
		pkt.setDestId(pkt.getSrcId());

		// sends back to the sender
		sendNow(pkt.getSrcId(), CloudSimTags.INFOPKT_RETURN, pkt);
	}

	/**
	 * Process the event for an User/Broker who wants to know the status of a Cloudlet. This
	 * PowerDatacenter will then send the status back to the User/Broker.
	 * 
	 * @param ev a Sim_event object
	 * @pre ev != null
	 * @post $none
	 */
	protected void processCloudletStatus(SimEvent ev) {
		int cloudletId = 0;
		int userId = 0;
		int vmId = 0;
		int status = -1;

		try {
			// if a sender using cloudletXXX() methods
			int data[] = (int[]) ev.getData();
			cloudletId = data[0];
			userId = data[1];
			vmId = data[2];

			status = getVmAllocationPolicy().getHost(vmId, userId).getVm(vmId,userId).getCloudletScheduler()
					.getCloudletStatus(cloudletId);
		}

		// if a sender using normal send() methods
		catch (ClassCastException c) {
			try {
				Cloudlet cl = (Cloudlet) ev.getData();
				cloudletId = cl.getCloudletId();
				userId = cl.getUserId();

				status = getVmAllocationPolicy().getHost(vmId, userId).getVm(vmId,userId)
						.getCloudletScheduler().getCloudletStatus(cloudletId);
			} catch (Exception e) {
				Log.printLine(getName() + ": Error in processing CloudSimTags.CLOUDLET_STATUS");
				Log.printLine(e.getMessage());
				return;
			}
		} catch (Exception e) {
			Log.printLine(getName() + ": Error in processing CloudSimTags.CLOUDLET_STATUS");
			Log.printLine(e.getMessage());
			return;
		}

		int[] array = new int[3];
		array[0] = getId();
		array[1] = cloudletId;
		array[2] = status;

		int tag = CloudSimTags.CLOUDLET_STATUS;
		sendNow(userId, tag, array);
	}

	/**
	 * Here all the method related to VM requests will be received and forwarded to the related
	 * method.
	 * 
	 * @param ev the received event
	 * @pre $none
	 * @post $none
	 */
	protected void processOtherEvent(SimEvent ev) {
		if (ev == null) {
			Log.printLine(getName() + ".processOtherEvent(): Error - an event is null.");
		}
		else
		{
			System.out.println("processing unknow event " + ev.getEventTypeName());
		}
	}

	/**
	 * Process the event for an User/Broker who wants to create a VM in this PowerDatacenter. This
	 * PowerDatacenter will then send the status back to the User/Broker.
	 * 
	 * @param ev a Sim_event object
	 * @param ack the ack
	 * @pre ev != null
	 * @post $none
	 */
	protected void processVmCreate(SimEvent ev, boolean ack) {
		Vm vm = (Vm) ev.getData();

		boolean result = getVmAllocationPolicy().allocateHostForVm(vm);
				
		
		if (ack) {
			int[] data = new int[3];
			data[0] = getId();
			data[1] = vm.getId();

			if (result) {
				data[2] = CloudSimTags.TRUE;
			} else {
				data[2] = CloudSimTags.FALSE;
			}
			send(vm.getUserId(), CloudSim.getMinTimeBetweenEvents(), CloudSimTags.VM_CREATE_ACK, data);
		}

		if (result) {
			getVmList().add(vm);

			if (vm.isBeingInstantiated()) {
				vm.setBeingInstantiated(false);
			}

			vm.updateVmProcessing(CloudSim.clock(), getVmAllocationPolicy().getHost(vm).getVmScheduler()
					.getAllocatedMipsForVm(vm));
			if (CloudSim.clock() > 1)
				Log.printLine(CloudSim.clock() + ": [Datacenter.processVmCreate] vm#" + vm.getId() + " is created on host id " + vm.getHost().getId());
			
			scheduleVmReplicate(vm);
		}
		else
		{
			Log.printLine("[Datacenter.processVmCreate] vm#" + vm.getId() + " is NOT created, therefore destroying this vm and all it's associated cloudlets ");
			scheduleVmDestruction(vm);
		}

	}
	
	
	protected void processVmReplicate(SimEvent ev) {
		Vm vm = (Vm) ev.getData();
		
		Vm cpyVM = getCopyVm(vm.getId());
		if (cpyVM == null)		
			getVmAllocationPolicy().replicate(vm);
	}

	/**
	 * Process the event for an User/Broker who wants to destroy a VM previously created in this
	 * PowerDatacenter. This PowerDatacenter may send, upon request, the status back to the
	 * User/Broker.
	 * 
	 * @param ev a Sim_event object
	 * @param ack the ack
	 * @pre ev != null
	 * @post $none
	 */
	protected void processVmDestroy(SimEvent ev, boolean ack) {
		Vm vm = (Vm) ev.getData();
		getVmAllocationPolicy().deallocateHostForVm(vm);

		if (ack) {
			int[] data = new int[3];
			data[0] = getId();
			data[1] = vm.getId();
			data[2] = CloudSimTags.TRUE;

			sendNow(vm.getUserId(), CloudSimTags.VM_DESTROY_ACK, data);
		}

		getVmList().remove(vm);
	}

	protected void processVmDestroyALL()
	{
		
		for(Host host: getVmAllocationPolicy().getHostList())
		{
			for (Vm vm: host.getVmList())
			{
				scheduleVmDestroy(vm);
				
			}
		}
	}
	/**
	 * Process the event for an User/Broker who wants to migrate a VM. This PowerDatacenter will
	 * then send the status back to the User/Broker.
	 * 
	 * @param ev a Sim_event object
	 * @pre ev != null
	 * @post $none
	 */
	protected void processVmMigrate(SimEvent ev, boolean ack) {
		Object tmp = ev.getData();
		if (!(tmp instanceof Map<?, ?>)) {
			throw new ClassCastException("The data object must be Map<String, Object>");
		}

		@SuppressWarnings("unchecked")
		Map<String, Object> migrate = (HashMap<String, Object>) tmp;

		Vm vm = (Vm) migrate.get("vm");
		Host host = (Host) migrate.get("host");

		getVmAllocationPolicy().deallocateHostForVm(vm);
		host.removeMigratingInVm(vm);
		
		boolean result = getVmAllocationPolicy().allocateHostForVm(vm, host);
		
		if (!result) 
		{
			
			Log.printLine(CloudSim.clock() + ": " + getName() + "[Datacenter.processVmMigrate] VM allocation to the destination failed, system is ABORTED");			
			System.exit(0);
		}

		if (ack) {
			int[] data = new int[3];
			data[0] = getId();
			data[1] = vm.getId();

			if (result) {
				data[2] = CloudSimTags.TRUE;
			} else {
				data[2] = CloudSimTags.FALSE;
			}
			sendNow(ev.getSource(), CloudSimTags.VM_CREATE_ACK, data);
		}

		Log.formatLine(
				"%.2f: Migration of VM #%d to Host #%d is completed",
				CloudSim.clock(),
				vm.getId(),
				host.getId());
		vm.setInMigration(false);
	}

	/**
	 * Processes a Cloudlet based on the event type.
	 * 
	 * @param ev a Sim_event object
	 * @param type event type
	 * @pre ev != null
	 * @pre type > 0
	 * @post $none
	 */
	protected void processCloudlet(SimEvent ev, int type) {
		int cloudletId = 0;
		int userId = 0;
		int vmId = 0;

		try { 
			int data[] = (int[]) ev.getData();
			cloudletId = data[0];
			userId = data[1];
			vmId = data[2];
		}

		
		catch (ClassCastException c) {
			try {
				Cloudlet cl = (Cloudlet) ev.getData();
				cloudletId = cl.getCloudletId();
				userId = cl.getUserId();
				vmId = cl.getVmId();
			} catch (Exception e) {
				Log.printLine(super.getName() + ": Error in processing Cloudlet");
				Log.printLine(e.getMessage());
				return;
			}
		} catch (Exception e) {
			Log.printLine(super.getName() + ": Error in processing a Cloudlet.");
			Log.printLine(e.getMessage());
			return;
		}

		// begins executing ....
		switch (type) {
			case CloudSimTags.CLOUDLET_CANCEL:
				processCloudletCancel(cloudletId, userId, vmId);
				break;

			case CloudSimTags.CLOUDLET_PAUSE:
				processCloudletPause(cloudletId, userId, vmId, false);
				break;

			case CloudSimTags.CLOUDLET_PAUSE_ACK:
				processCloudletPause(cloudletId, userId, vmId, true);
				break;

			case CloudSimTags.CLOUDLET_RESUME:
				processCloudletResume(cloudletId, userId, vmId, false);
				break;

			case CloudSimTags.CLOUDLET_RESUME_ACK:
				processCloudletResume(cloudletId, userId, vmId, true);
				break;
			default:
				break;
		}

	}

	/**
	 * Process the event for an User/Broker who wants to move a Cloudlet.
	 * 
	 * @param receivedData information about the migration
	 * @param type event tag
	 * @pre receivedData != null
	 * @pre type > 0
	 * @post $none
	 */
	protected void processCloudletMove(int[] receivedData, int type) {
		updateCloudletProcessing();

		int[] array = receivedData;
		int cloudletId = array[0];
		int userId = array[1];
		int vmId = array[2];
		int vmDestId = array[3];
		int destId = array[4];

		int hostIdx = getHostIndexOfVm(vmDestId);
		Cloudlet cl = null;
		
		if(hostIdx != -1)
		{
			cl = getVmAllocationPolicy().getHostList().get(hostIdx).getVm(vmDestId, userId).getCloudletScheduler().cloudletCancel(cloudletId);
		}
		else
		{
			sendNow(this.getId(), CloudSimTags.CLOUDLET_CANCEL, cl);
		}

		boolean failed = false;
		if (cl == null) {
			failed = true;
		} 
		else 
		{
			if (cl.getCloudletStatus() == Cloudlet.SUCCESS) {
				int[] data = new int[3];
				data[0] = getId();
				data[1] = cloudletId;
				data[2] = 0;
				sendNow(cl.getUserId(), CloudSimTags.CLOUDLET_SUBMIT_ACK, data);
				sendNow(cl.getUserId(), CloudSimTags.CLOUDLET_RETURN, cl);
			}

			cl.setVmId(vmDestId);
			if (destId == getId()) 
			{
				Vm vm = null;
				try
				{
					vm = getVmAllocationPolicy().getHostList().get(hostIdx).getVm(vmDestId, userId);
				}
				catch (NullPointerException e)
				{
					vm = getVmList().get(getVmForCloudletMigration(vmId, userId));
					Log.printLine("ERROR  " + getName() + ".processCloudletMove : NullPointerException for cloudletId " + cloudletId);
				}
				if (vm == null) {
					failed = true;
				} else {
					double fileTransferTime = predictFileTransferTime(cl.getRequiredFiles());
					vm.getCloudletScheduler().cloudletSubmit(cl, fileTransferTime);
				}
			} else {
				int tag = ((type == CloudSimTags.CLOUDLET_MOVE_ACK) ? CloudSimTags.CLOUDLET_SUBMIT_ACK
						: CloudSimTags.CLOUDLET_SUBMIT);
				sendNow(destId, tag, cl);

			}
		}

		if (type == CloudSimTags.CLOUDLET_MOVE_ACK) {
			int[] data = new int[3];
			data[0] = getId();
			data[1] = cloudletId;
			if (failed) {
				data[2] = 0;
			} else {
				data[2] = 1;
			}
			sendNow(cl.getUserId(), CloudSimTags.CLOUDLET_SUBMIT_ACK, data);
		}
	}

	/**
	 * Processes a Cloudlet submission.
	 * 
	 * @param ev a SimEvent object
	 * @param ack an acknowledgement
	 * @pre ev != null
	 * @post $none
	 */
	protected void processCloudletSubmit(SimEvent ev, boolean ack) {
		updateCloudletProcessing();

		try {
			Cloudlet cl = (Cloudlet) ev.getData();

			if (cl.isFinished()) {
				String name = CloudSim.getEntityName(cl.getUserId());
				Log.printLine(getName() + ": Warning - Cloudlet #" + cl.getCloudletId() + " owned by " + name
						+ " is already completed/finished.");

				if (ack) {
					int[] data = new int[3];
					data[0] = getId();
					data[1] = cl.getCloudletId();
					data[2] = CloudSimTags.FALSE;

					int tag = CloudSimTags.CLOUDLET_SUBMIT_ACK;
					sendNow(cl.getUserId(), tag, data);
				}

				sendNow(cl.getUserId(), CloudSimTags.CLOUDLET_RETURN, cl);

				return;
			}

			cl.setResourceParameter(getId(), getCharacteristics().getCostPerSecond(), getCharacteristics()
					.getCostPerBw());

			int userId = cl.getUserId();
			int vmId = cl.getVmId();

			double fileTransferTime = predictFileTransferTime(cl.getRequiredFiles());

			Host host = getVmAllocationPolicy().getHost(vmId, userId);
			Vm vm = null;

			if (host != null)
			{
				vm = host.getVm(vmId, userId);
			
			
			CloudletScheduler scheduler = vm.getCloudletScheduler();
			
			double estimatedFinishTime = scheduler.cloudletSubmit(cl, fileTransferTime);
			

			if (estimatedFinishTime > 0.0 && !Double.isInfinite(estimatedFinishTime)) {
				estimatedFinishTime += fileTransferTime;
				send(getId(), estimatedFinishTime, CloudSimTags.VM_DATACENTER_EVENT);
			}
			}

			if (ack) {
				int[] data = new int[3];
				data[0] = getId();
				data[1] = cl.getCloudletId();
				data[2] = CloudSimTags.TRUE;

				int tag = CloudSimTags.CLOUDLET_SUBMIT_ACK;
				sendNow(cl.getUserId(), tag, data);
			}
		} catch (ClassCastException c) {
			Log.printLine(getName() + ".processCloudletSubmit(): " + "ClassCastException error.");
			c.printStackTrace();
		} catch (Exception e) {
			Log.printLine(getName() + ".processCloudletSubmit(): " + "Exception error msg:" + e.toString() + " ABORTING THE SIMULATION");
			e.printStackTrace();
			System.exit(0);
		}

		checkCloudletCompletion();
	}

	/**
	 * Predict file transfer time.
	 * 
	 * @param requiredFiles the required files
	 * @return the double
	 */
	protected double predictFileTransferTime(List<String> requiredFiles) {
		double time = 0.0;

		Iterator<String> iter = requiredFiles.iterator();
		while (iter.hasNext()) {
			String fileName = iter.next();
			for (int i = 0; i < getStorageList().size(); i++) {
				Storage tempStorage = getStorageList().get(i);
				File tempFile = tempStorage.getFile(fileName);
				if (tempFile != null) {
					time += tempFile.getSize() / tempStorage.getMaxTransferRate();
					break;
				}
			}
		}
		return time;
	}

	/**
	 * Processes a Cloudlet resume request.
	 * 
	 * @param cloudletId resuming cloudlet ID
	 * @param userId ID of the cloudlet's owner
	 * @param ack $true if an ack is requested after operation
	 * @param vmId the vm id
	 * @pre $none
	 * @post $none
	 */
	protected void processCloudletResume(int cloudletId, int userId, int vmId, boolean ack) 
	{
		try
		{
			int hostIdx = getHostIndexOfVm(vmId);
			if (hostIdx == -1)
			{
				return;
			}
			double eventTime = getVmAllocationPolicy().getHostList().get(getHostIndexOfVm(vmId)).getVm(vmId,userId).getCloudletScheduler().cloudletResume(cloudletId);

			boolean status = false;
			if (eventTime > 0.0) { 
				status = true;
				if (eventTime > CloudSim.clock()) {
					schedule(getId(), eventTime, CloudSimTags.VM_DATACENTER_EVENT);
				}
			}
	
			if (ack) {
				int[] data = new int[3];
				data[0] = getId();
				data[1] = cloudletId;
				if (status) {
					data[2] = CloudSimTags.TRUE;
				} else {
					data[2] = CloudSimTags.FALSE;
				}
				sendNow(userId, CloudSimTags.CLOUDLET_RESUME_ACK, data);
			}
		}catch(Exception e)
		{
			System.out.println("*** ERROR\t" + CloudSim.clock() + ": [Datacenter.processCloudletResume] cloudlet id " + cloudletId + " vm# = " + vmId + "\n System aborted");
			e.printStackTrace();
			System.exit(0);			
		}
	}

	/**
	 * Processes a Cloudlet pause request.
	 * 
	 * @param cloudletId resuming cloudlet ID
	 * @param userId ID of the cloudlet's owner
	 * @param ack $true if an ack is requested after operation
	 * @param vmId the vm id
	 * @pre $none
	 * @post $none
	 */
	protected void processCloudletPause(int cloudletId, int userId, int vmId, boolean ack)
	{
		int hostIdx = getHostIndexOfVm(vmId);
		Cloudlet cl = null;
		
		boolean status = false;
		
		if(hostIdx != -1)
		{
			status = getVmAllocationPolicy().getHostList().get(hostIdx).getVm(vmId, userId).getCloudletScheduler().cloudletPause(cloudletId);
		}

		if (ack) {
			int[] data = new int[3];
			data[0] = getId();
			data[1] = cloudletId;
			if (status) {
				data[2] = CloudSimTags.TRUE;
			} else {
				data[2] = CloudSimTags.FALSE;
			}
			sendNow(userId, CloudSimTags.CLOUDLET_PAUSE_ACK, data);
		}
	}

	/**
	 * Processes a Cloudlet cancel request.
	 * 
	 * @param cloudletId resuming cloudlet ID
	 * @param userId ID of the cloudlet's owner
	 * @param vmId the vm id
	 * @pre $none
	 * @post $none
	 */
	protected void processCloudletCancel(int cloudletId, int userId, int vmId) {
		int hostIdx = getHostIndexOfVm(vmId);
		Cloudlet cl = null;
		
		if(hostIdx != -1)
		{
			cl = getVmAllocationPolicy().getHostList().get(hostIdx).getVm(vmId, userId).getCloudletScheduler().cloudletCancel(cloudletId);
		}
		
		if (cl != null)
			sendNow(userId, CloudSimTags.CLOUDLET_RETURN, cl);
	}

	
	/*
	 * process a failed host
	 * this method is called by process event, to handle a failure happened in a PM
	 */
	protected void processHostFailure(SimEvent ev) {
		Host host = (Host) ev.getData();

		failedHostList.add(host);
		
		for (Vm vm : host.getVmList())
		{		
			if (getVmAllocationPolicy().usingReplication(vm)) 
			{				
				Vm cpyVM = getCopyVm(vm.getId());
				
				if (isVmSetToDestroy(-1 * vm.getId()))
				{
					String msg = "[processHostFailure] " + CloudSim.clock() + " vm# " + vm.getId() + " & its copy are failung, its created again";
					
					Log.printLine(msg);
					scheduleVmRestart(vm);
					continue;
				}
				
				scheduleCloudletMoving(vm);
				scheduleVmDestroy(vm);
				
				if (getVmList().contains(vm))
				{
					getVmList().add(cpyVM);
				}
				
				scheduleVmReplicate(cpyVM);
			}
			else
			{
				scheduleVmRestart(vm);
			}
		}
		
		
		hostRemoval(host);
	}
	
	protected void hostRemoval(Host host){
		List<? extends Host> hostList = getVmAllocationPolicy().getHostList();

		hostList.get(hostList.indexOf(host)).setFailed(true);		
		getVmAllocationPolicy().setHostList(hostList);
		
		Log.printLine(CloudSim.clock() + ": " + getName() + ": host id " + host.getId() + " has been removed");
	}
	
	protected void processHostRemoval(SimEvent ev){
		Host host = (Host) ev.getData();
		List<? extends Host> hostList = getVmAllocationPolicy().getHostList();
		hostList.get(hostList.indexOf(host)).setFailed(true);		
		getVmAllocationPolicy().setHostList(hostList);
		Log.printLine(CloudSim.clock() + ": " + getName() + ": host id " + host.getId() + " has been removed");

	}
	
	protected void processHostJoining(SimEvent ev){
		List<Host> hostList = getVmAllocationPolicy().getHostList();
		Host host = (Host) ev.getData();

		failedHostList.remove(host);

		int hostIndx = getVmAllocationPolicy().getHostList().indexOf(host);
		if(hostIndx != -1)
		{
			getVmAllocationPolicy().getHostList().get(hostIndx).setFailed(false);
		}
		else
		{
			System.out.println(CloudSim.clock() + ": [datacentre.processHostJoining]: trying to add a host that is not already in the hostList .. note the new way is being used ... host id " + host.getId());
			System.exit(0);
		}
		Log.printLine(CloudSim.clock() + ": " + getName() + ": host id " + host.getId() + " has joined\n");

	}
	
	/**
	 * Updates processing of each cloudlet running in this PowerDatacenter. It is necessary because
	 * Hosts and VirtualMachines are simple objects, not entities. So, they don't receive events and
	 * updating cloudlets inside them must be called from the outside.
	 * 
	 * @pre $none
	 * @post $none
	 */
	protected void updateCloudletProcessing() {
		// if some time passed since last processing
		// R: for term is to allow loop at simulation start. Otherwise, one initial
		// simulation step is skipped and schedulers are not properly initialized
		if (CloudSim.clock() < 0.111 || CloudSim.clock() > getLastProcessTime() + CloudSim.getMinTimeBetweenEvents()) {
			List<? extends Host> list = getVmAllocationPolicy().getHostList();
			double smallerTime = Double.MAX_VALUE;
			// for each host...
			for (int i = 0; i < list.size(); i++) {
				Host host = list.get(i);
				// inform VMs to update processing
				double time = host.updateVmsProcessing(CloudSim.clock());
				// what time do we expect that the next cloudlet will finish?
				if (time < smallerTime) {
					smallerTime = time;
				}
			}
			// gurantees a minimal interval before scheduling the event
			if (smallerTime < CloudSim.clock() + CloudSim.getMinTimeBetweenEvents() + 0.01) {
				smallerTime = CloudSim.clock() + CloudSim.getMinTimeBetweenEvents() + 0.01;
			}
			if (smallerTime != Double.MAX_VALUE) {
				schedule(getId(), (smallerTime - CloudSim.clock()), CloudSimTags.VM_DATACENTER_EVENT);
			}
			setLastProcessTime(CloudSim.clock());
		}
	}

	/**
	 * Verifies if some cloudlet inside this PowerDatacenter already finished. If yes, send it to
	 * the User/Broker
	 * 
	 * @pre $none
	 * @post $none
	 */
	protected void checkCloudletCompletion() {
		List<? extends Host> list = getVmAllocationPolicy().getHostList();
		for (int i = 0; i < list.size(); i++) {
			Host host = list.get(i);
			for (Vm vm : host.getVmList()) {
				while (vm.getCloudletScheduler().isFinishedCloudlets()) {
					Cloudlet cl = vm.getCloudletScheduler().getNextFinishedCloudlet();
					if (cl != null) {
						sendNow(cl.getUserId(), CloudSimTags.CLOUDLET_RETURN, cl);
					}
				}
			}
		}
	}

	/**
	 * Adds a file into the resource's storage before the experiment starts. If the file is a master
	 * file, then it will be registered to the RC when the experiment begins.
	 * 
	 * @param file a DataCloud file
	 * @return a tag number denoting whether this operation is a success or not
	 */
	public int addFile(File file) {
		if (file == null) {
			return DataCloudTags.FILE_ADD_ERROR_EMPTY;
		}

		if (contains(file.getName())) {
			return DataCloudTags.FILE_ADD_ERROR_EXIST_READ_ONLY;
		}

		// check storage space first
		if (getStorageList().size() <= 0) {
			return DataCloudTags.FILE_ADD_ERROR_STORAGE_FULL;
		}

		Storage tempStorage = null;
		int msg = DataCloudTags.FILE_ADD_ERROR_STORAGE_FULL;

		for (int i = 0; i < getStorageList().size(); i++) {
			tempStorage = getStorageList().get(i);
			if (tempStorage.getAvailableSpace() >= file.getSize()) {
				tempStorage.addFile(file);
				msg = DataCloudTags.FILE_ADD_SUCCESSFUL;
				break;
			}
		}

		return msg;
	}

	/**
	 * Checks whether the resource has the given file.
	 * 
	 * @param file a file to be searched
	 * @return <tt>true</tt> if successful, <tt>false</tt> otherwise
	 */
	protected boolean contains(File file) {
		if (file == null) {
			return false;
		}
		return contains(file.getName());
	}

	/**
	 * Checks whether the resource has the given file.
	 * 
	 * @param fileName a file name to be searched
	 * @return <tt>true</tt> if successful, <tt>false</tt> otherwise
	 */
	protected boolean contains(String fileName) {
		if (fileName == null || fileName.length() == 0) {
			return false;
		}

		Iterator<Storage> it = getStorageList().iterator();
		Storage storage = null;
		boolean result = false;

		while (it.hasNext()) {
			storage = it.next();
			if (storage.contains(fileName)) {
				result = true;
				break;
			}
		}

		return result;
	}

	/**
	 * Deletes the file from the storage. Also, check whether it is possible to delete the file from
	 * the storage.
	 * 
	 * @param fileName the name of the file to be deleted
	 * @return the error message
	 */
	private int deleteFileFromStorage(String fileName) {
		Storage tempStorage = null;
		File tempFile = null;
		int msg = DataCloudTags.FILE_DELETE_ERROR;

		for (int i = 0; i < getStorageList().size(); i++) {
			tempStorage = getStorageList().get(i);
			tempFile = tempStorage.getFile(fileName);
			tempStorage.deleteFile(fileName, tempFile);
			msg = DataCloudTags.FILE_DELETE_SUCCESSFUL;
		} // end for

		return msg;
	}

	/*
	 * (non-Javadoc)
	 * @see cloudsim.core.SimEntity#shutdownEntity()
	 */
	@Override
	public void shutdownEntity() {
		Log.printLine(getName() + " is shutting down...");
	}

	/*
	 * (non-Javadoc)
	 * @see cloudsim.core.SimEntity#startEntity()
	 */
	@Override
	public void startEntity() {
		Log.printLine(getName() + " is starting... ");
		int gisID = CloudSim.getEntityId(regionalCisName);
		if (gisID == -1) {
			gisID = CloudSim.getCloudInfoServiceEntityId();
		}

		// send the registration to GIS
		sendNow(gisID, CloudSimTags.REGISTER_RESOURCE, getId());
		// Below method is for a child class to override
		registerOtherEntity();
	}

	/**
	 * Gets the host list.
	 * 
	 * @return the host list
	 */
	@SuppressWarnings("unchecked")
	public <T extends Host> List<T> getHostList() {
		return (List<T>) getCharacteristics().getHostList();
	}

	/**
	 * Gets the characteristics.
	 * 
	 * @return the characteristics
	 */
	protected DatacenterCharacteristics getCharacteristics() {
		return characteristics;
	}

	/**
	 * Sets the characteristics.
	 * 
	 * @param characteristics the new characteristics
	 */
	protected void setCharacteristics(DatacenterCharacteristics characteristics) {
		this.characteristics = characteristics;
	}

	/**
	 * Gets the regional cis name.
	 * 
	 * @return the regional cis name
	 */
	protected String getRegionalCisName() {
		return regionalCisName;
	}

	/**
	 * Sets the regional cis name.
	 * 
	 * @param regionalCisName the new regional cis name
	 */
	protected void setRegionalCisName(String regionalCisName) {
		this.regionalCisName = regionalCisName;
	}

	/**
	 * Gets the vm allocation policy.
	 * 
	 * @return the vm allocation policy
	 */
	public VmAllocationPolicy getVmAllocationPolicy() {
		return vmAllocationPolicy;
	}

	/**
	 * Sets the vm allocation policy.
	 * 
	 * @param vmAllocationPolicy the new vm allocation policy
	 */
	protected void setVmAllocationPolicy(VmAllocationPolicy vmAllocationPolicy) {
		this.vmAllocationPolicy = vmAllocationPolicy;
	}

	/**
	 * Gets the last process time.
	 * 
	 * @return the last process time
	 */
	protected double getLastProcessTime() {
		return lastProcessTime;
	}

	/**
	 * Sets the last process time.
	 * 
	 * @param lastProcessTime the new last process time
	 */
	protected void setLastProcessTime(double lastProcessTime) {
		this.lastProcessTime = lastProcessTime;
	}

	/**
	 * Gets the storage list.
	 * 
	 * @return the storage list
	 */
	protected List<Storage> getStorageList() {
		return storageList;
	}

	/**
	 * Sets the storage list.
	 * 
	 * @param storageList the new storage list
	 */
	protected void setStorageList(List<Storage> storageList) {
		this.storageList = storageList;
	}

	/**
	 * Gets the vm list.
	 * 
	 * @return the vm list
	 */
	@SuppressWarnings("unchecked")
	public <T extends Vm> List<T> getVmList() {
		return (List<T>) vmList;
	}

	/**
	 * Sets the vm list.
	 * 
	 * @param vmList the new vm list
	 */
	protected <T extends Vm> void setVmList(List<T> vmList) {
		this.vmList = vmList;
	}

	/**
	 * Gets the scheduling interval.
	 * 
	 * @return the scheduling interval
	 */
	protected double getSchedulingInterval() {
		return schedulingInterval;
	}

	/**
	 * Sets the scheduling interval.
	 * 
	 * @param schedulingInterval the new scheduling interval
	 */
	protected void setSchedulingInterval(double schedulingInterval) {
		this.schedulingInterval = schedulingInterval;
	}

	/*
	 * printDetails: to print the details of hosts
	 */
	public void printDetails()
	{
		double utilisation;
		for(Host host: getHostList())
		{
			utilisation = host.getUtilisationPercentage();
			Log.printLine(CloudSim.clock() + ": host id " + host.getId() + ", MIPS: " + host.getTotalMips() +" Current utilisation = " + utilisation + "%, allocated VMs:");
			
			for(Vm vm: host.getVmList())
			{
				Log.printLine("\t\tvm #" + vm.getId()+ ", MIPS: " + vm.getCurrentRequestedTotalMips());
			}
		}
	}
	/**
	 * set a host to fail, for ever
	 * @param hostId the id of the failing host
	 * @param failureTime time when the failure gonna happen
	 */
	public void issueFailure(int hostId, double failureTime)
	{
		issueFailure(hostId, failureTime, -1);
	}
	
	public void joinHost(Host host, double joiningTime)
	{
		scheduleHostAlive(joiningTime, host);
	}
	/*
	 * fail host with id id after time time
	 */
	public void issueFailure(int hostId, double failureTime, double aliveTime)
	{	
		for (Host host: getCharacteristics().getHostList())
		{
			if (host.getId() == hostId)
			{
				scheduleHostFailure(failureTime, host);
				
				if (aliveTime >= 0)
					scheduleHostAlive(aliveTime, host);
				break;
			}
		}
	}
	/*
	 * Schedule failure in Cloud nodes
	 */
	protected void scheduleHostFailure(double failureTime, Host host)
	{
		int src = this.getId();
		CloudSim.send(src , src, failureTime, CloudSimTags.HOST_FAIL, host);
	}
	/*
	 * Schedule the event the failed host to go alive again 
	 */
	protected void scheduleHostAlive(double aliveTime, Host host){
		int src = this.getId();
		CloudSim.send(src , src, aliveTime, CloudSimTags.HOST_ALIVE, host);
	}

	protected void scheduleCloudLetPenalty(Vm vm)
	{
		
		DatacenterBroker broker = (DatacenterBroker) CloudSim.getEntity(vm.getUserId());
		List<Cloudlet> cloudletList = broker.getCloudletSubmittedList();
		Cloudlet cloudlet;

		for (int i=0; i < cloudletList.size(); i++)
		{
			cloudlet = cloudletList.get(i);
			if (cloudlet.getVmId() == vm.getId() &&  cloudlet.getStatus() == Cloudlet.INEXEC )
			{
				sendNow(this.getId(), CloudSimTags.CLOUDLET_CANCEL, cloudlet);			
			}
		}
	}
	protected void scheduleCloudletMoving(Vm vm)
	{			
		DatacenterBroker broker = (DatacenterBroker) CloudSim.getEntity(vm.getUserId());		
		
		for(Cloudlet cloudlet: broker.cloudletSubmittedList)
		{
			if (cloudlet.getVmId() == vm.getId())
			{
				if (cloudlet.getStatus() != Cloudlet.SUCCESS)
				{					
					if (getCopyVm(vm.getId()) == null)
						System.out.println("Error in scheduleCloudletMoving ... moving a cloud to no copy at all");
					
					int[] data = {cloudlet.getCloudletId(), vm.getUserId(), vm.getId(), -1 * vm.getId(), this.getId()}; 
					sendNow(getId(),  CloudSimTags.CLOUDLET_MOVE, data);
				}
			}
		}		
	}

	protected void scheduleVmCreate(Vm vm)
	{
		vm.setBeingInstantiated(true);
		send(this.getId(), 2 * CloudSim.getMinTimeBetweenEvents(), CloudSimTags.VM_CREATE, vm);
	}
	
	protected void scheduleVmDestroy(Vm vm)
	{
		sendNow(this.getId(),CloudSimTags.VM_DESTROY, vm);
	}
	/**
	 * cancels all cloudlets running on this VM, schedule a destruction and restasrting of this VM
	 * @param vm
	 */
	protected void scheduleVmRestart(Vm vm)
	{			
		DatacenterBroker broker = (DatacenterBroker) CloudSim.getEntity(vm.getUserId());
		for(Cloudlet cloudlet: broker.cloudletSubmittedList)
		{
			if (cloudlet.getVmId() == vm.getId())
			{
				if (cloudlet.getStatus() == Cloudlet.INEXEC)
				{
					sendNow(this.getId(), CloudSimTags.CLOUDLET_CANCEL, cloudlet);
				}
				
				else if (cloudlet.getStatus() != Cloudlet.SUCCESS)		
				{
					int[] data = {cloudlet.getCloudletId(), vm.getUserId(), vm.getId()};

					sendNow(getId(),  CloudSimTags.CLOUDLET_PAUSE, data);
					send(getId(), 3 * CloudSim.getMinTimeBetweenEvents(), CloudSimTags.CLOUDLET_RESUME, data);
				}
			}
		}
		sendNow(this.getId(),CloudSimTags.VM_DESTROY, vm);
		
		scheduleVmCreate(vm);
	}
	
	protected void scheduleVmReplicate(Vm vm)
	{
		send(this.getId(), 2 * CloudSim.getMinTimeBetweenEvents(), CloudSimTags.VM_REPLICATE, vm);
	}
	/**
	 * destroys the VM and cancels all associated cloudlets except success cloudlets
	 * @param vm
	 */
	protected void scheduleVmDestruction(Vm vm)
	{
		DatacenterBroker broker = (DatacenterBroker) CloudSim.getEntity(vm.getUserId());
		for(Cloudlet cloudlet: broker.cloudletSubmittedList)
		{
			if (cloudlet.getVmId() == vm.getId())
				if (cloudlet.getStatus() != Cloudlet.SUCCESS)
					sendNow(this.getId(), CloudSimTags.CLOUDLET_CANCEL, cloudlet);
		}
		sendNow(this.getId(),CloudSimTags.VM_DESTROY, vm);
	}
	
	protected void scheduleVmCreation(Vm vm)
	{
		vm.setBeingInstantiated(true);
		send(this.getId(), 2 * CloudSim.getMinTimeBetweenEvents(), CloudSimTags.VM_CREATE, vm);
	}
	
	protected void scheduleVmMigration(Vm vm)
	{					
		int migrateToHostIndex = getVmAllocationPolicy().getHostIndexForAllocation(vm);		
		
		
		if (migrateToHostIndex == -1)	
		{
			
			sendNow(vm.getUserId(), CloudSimTags.VM_DESTROY, vm);
			
			Log.printLine(CloudSim.clock() + ": [vm destruction] \t vm " + vm.getId() + " is set to be destroyed, no availability\t");
			send(this.getId(), CloudSim.getMinTimeBetweenEvents(), CloudSimTags.VM_DESTROY, vm);
		}
		else
		{			
			Map<String, Object> migrate = new HashMap<String, Object>();

			Host host = this.getHostList().get(migrateToHostIndex);
			host.addMigratingInVm(vm);
			
			migrate.put("vm", vm);
			migrate.put("host", host);
			
			sendNow(this.getId(), CloudSimTags.VM_MIGRATE, migrate);
		}
	}
	
	protected void scheduleVmMigrationPwr(Vm vm)
	{					
		int migrateToHostIndex = getVmAllocationPolicy().getHostIndexForMigration(vm);		
		
		
		if (migrateToHostIndex != -1)	
		{			
			Map<String, Object> migrate = new HashMap<String, Object>();

			Host host = this.getHostList().get(migrateToHostIndex);
			host.addMigratingInVm(vm);
			
			migrate.put("vm", vm);
			migrate.put("host", host);
			
			send(this.getId(), CloudSim.getMinTimeBetweenEvents(), CloudSimTags.VM_MIGRATE, migrate);
			
		}
	}


	protected void scheduleHostRemoving(Host host){
		send(this.getId(), CloudSim.getMinTimeBetweenEvents(), CloudSimTags.HOST_REMOVE, host);
	}
	
	
	protected int getVmIdToMigrateCloudlet(Vm vm)
	{
		int retIndex =-1;
		Random r = new Random();
		
		while(retIndex < 0)
		{
			int vmIndex = r.nextInt() % vmList.size();
			
			if (vmIndex < 0)
				vmIndex = vmIndex * -1;
			
			if(vmList.get(vmIndex).getHost().getId() != vm.getHost().getId())
				retIndex = vmIndex;
		}
		return retIndex;
	}
	
	protected int getVmForCloudletMigration(int vmId, int userId)
	{
		return getVmAllocationPolicy().getVmForCloudletMigration(vmId, userId);
	}

	
	protected void setEnergyForHosts(double time)
	{
		List<Host> hostList = getVmAllocationPolicy().getHostList();
		
		for(Host host: hostList)
		{
			host.setEnergyConsumption(host.getEnergyConsumption() + time * host.getPower());
		}

		getVmAllocationPolicy().setHostList(hostList);
	}

	private int getHostIndexOfVm(int vmId)
	{
		int idx =0;
		for(Host host: getVmAllocationPolicy().getHostList())
		{
			for (Vm tmpVm: host.getVmList())
			{
				if (vmId == tmpVm.getId() )
					return idx;
			}
			idx++;
		}		
		return -1;
	}
	
	protected Vm getCopyVm(int vmId)
	{
		Vm vm = null;
		for(Host host: getVmAllocationPolicy().getHostList())
		{
			for (Vm tmpVm: host.getVmList())
			{
				if (vmId == -1 * tmpVm.getId() )
					return tmpVm;
			}
		}	
		return vm;
	}
	
	protected boolean isVmSetToDestroy(int vmId)
	{
		boolean ret = true;
		
		int hostIdx = getHostIndexOfVm(vmId);
		if (hostIdx >= 0)
		{
			ret = getVmAllocationPolicy().getHostList().get(hostIdx).isFailed();
		}
		
		return ret;
	}
	
}