/*
 * Title:        CloudSim Toolkit
 * Description:  CloudSim (Cloud Simulation) Toolkit for Modeling and Simulation of Clouds
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2009-2012, The University of Melbourne, Australia
 */

package org.cloudbus.cloudsim;

import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * VmAllocationPolicy is an abstract class that represents the provisioning policy of hosts to
 * virtual machines in a Datacentre. It supports two-stage commit of reservation of hosts: first, we
 * reserve the host and, once commited by the user, it is effectivelly allocated to he/she
 * 
 * @author Rodrigo N. Calheiros
 * @author Anton Beloglazov
 * @since CloudSim Toolkit 1.0
 */
public abstract class VmAllocationPolicy {

	/** The host list. */
	private List<? extends Host> hostList;

	/**
	 * Allocates a new VmAllocationPolicy object.
	 * 
	 * @param list Machines available in this Datacentre
	 * @pre $none
	 * @post $none
	 */
	public VmAllocationPolicy(List<? extends Host> list) {
		setHostList(list);
	}

	/**
	 * Allocates a host for a given VM. The host to be allocated is the one that was already
	 * reserved.
	 * 
	 * @param vm virtual machine which the host is reserved to
	 * @return $true if the host could be allocated; $false otherwise
	 * @pre $none
	 * @post $none
	 */
	public abstract boolean allocateHostForVm(Vm vm);

	/**
	 * Allocates a specified host for a given VM.
	 * 
	 * @param vm virtual machine which the host is reserved to
	 * @return $true if the host could be allocated; $false otherwise
	 * @pre $none
	 * @post $none
	 */
	public abstract boolean allocateHostForVm(Vm vm, Host host);

	/**
	 * Optimize allocation of the VMs according to current utilization.
	 * 
	 * @param vmList the vm list
	 * @param utilizationBound the utilization bound
	 * @param time the time
	 * @return the array list< hash map< string, object>>
	 */
	public abstract List<Map<String, Object>> optimizeAllocation(List<? extends Vm> vmList);

	/**
	 * Releases the host used by a VM.
	 * 
	 * @param vm the vm
	 * @pre $none
	 * @post $none
	 */
	public abstract void deallocateHostForVm(Vm vm);

	/**
	 * Get the host that is executing the given VM belonging to the given user.
	 * 
	 * @param vm the vm
	 * @return the Host with the given vmID and userID; $null if not found
	 * @pre $none
	 * @post $none
	 */
	public abstract Host getHost(Vm vm);

	/**
	 * Get the host that is executing the given VM belonging to the given user.
	 * 
	 * @param vmId the vm id
	 * @param userId the user id
	 * @return the Host with the given vmID and userID; $null if not found
	 * @pre $none
	 * @post $none
	 */
	public abstract Host getHost(int vmId, int userId);

	/**
	 * Sets the host list.
	 * 
	 * @param hostList the new host list
	 */
	public void setHostList(List<? extends Host> hostList) {
		this.hostList = hostList;
	}

	/**
	 * Gets the host list.
	 * 
	 * @return the host list
	 */
	@SuppressWarnings("unchecked")
	public <T extends Host> List<T> getHostList() {
		return (List<T>) hostList;
	}
	
	/**
	 * Gets the host index for migration of a vm 
	 * @param vm
	 * @return host index 
	 */
	public int getHostIndexForAllocation(Vm vm)
	{
		return -1;
	}
	/**
	 * adds free Pes when adding new host
	 * @param numberOfPes
	 */
	public void addFeePes(int numberOfPes)
	{
	}

	public int getVmForCloudletMigration(int vmId, int userId)
	{		
		return -1;
	}
	
	protected int getVmIdToMigrateCloudlet(Vm vm)
	{
		return -1;
	}
	
	public boolean replicate(Vm vm)
	{
		return false;
	}
	/**
	 * returns true if a replica of vm is found
	 */
	public boolean usingReplication(Vm vm)
	{
		return false;
	}
	
	public int getHostIndexForMigration(Vm vm)
	{
		return -1;
	}
}
