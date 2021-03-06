/*
 * Copyright (C) 2010 - present, Laszlo Csontos
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * 
 */

/**
 * 
 */
package info.geekinaction.autoalert.model.domain;

/**
 * 
 * Base class of representing database storage objects (ie. tablespaces and datafiles).
 * 
 * @author lcsontos
 * 
 */
public abstract class AbstractStorage extends AbstractAlertable<String> {

	private static final long serialVersionUID = 1L;
	
	protected String tablespaceName;
	protected Float sizeMaxMb;
	protected Float sizeMb;
	protected Float usedMb;
	protected Float freeMb;
	protected Float sizeRemainMb;
	protected Float usedPer;
	protected Float sizeRemainPer;
	
	/**
	 * @return the tablespaceName
	 */
	public String getTablespaceName() {
		return tablespaceName;
	}

	/**
	 * @param tablespaceName
	 *            the tablespaceName to set
	 */
	public void setTablespaceName(String tablespaceName) {
		this.tablespaceName = tablespaceName;
	}

	/**
	 * @return the sizeMaxMb
	 */
	public Float getSizeMaxMb() {
		return sizeMaxMb;
	}

	/**
	 * @param sizeMaxMb
	 *            the sizeMaxMb to set
	 */
	public void setSizeMaxMb(Float sizeMaxMb) {
		this.sizeMaxMb = sizeMaxMb;
	}

	/**
	 * @return the sizeMb
	 */
	public Float getSizeMb() {
		return sizeMb;
	}

	/**
	 * @param sizeMb
	 *            the sizeMb to set
	 */
	public void setSizeMb(Float sizeMb) {
		this.sizeMb = sizeMb;
	}

	/**
	 * @return the usedMb
	 */
	public Float getUsedMb() {
		return usedMb;
	}

	/**
	 * @param usedMb
	 *            the usedMb to set
	 */
	public void setUsedMb(Float usedMb) {
		this.usedMb = usedMb;
	}

	/**
	 * @return the freeMb
	 */
	public Float getFreeMb() {
		return freeMb;
	}

	/**
	 * @param freeMb
	 *            the freeMb to set
	 */
	public void setFreeMb(Float freeMb) {
		this.freeMb = freeMb;
	}

	/**
	 * @return the sizeRemainMb
	 */
	public Float getSizeRemainMb() {
		return sizeRemainMb;
	}

	/**
	 * @param sizeRemainMb
	 *            the sizeRemainMb to set
	 */
	public void setSizeRemainMb(Float sizeRemainMb) {
		this.sizeRemainMb = sizeRemainMb;
	}

	/**
	 * @return the usedPer
	 */
	public Float getUsedPer() {
		return usedPer;
	}

	/**
	 * @param usedPer
	 *            the usedPer to set
	 */
	public void setUsedPer(Float usedPer) {
		this.usedPer = usedPer;
	}

	/**
	 * @return the sizeRemainPer
	 */
	public Float getSizeRemainPer() {
		return sizeRemainPer;
	}

	/**
	 * @param sizeRemainPer
	 *            the sizeRemainPer to set
	 */
	public void setSizeRemainPer(Float sizeRemainPer) {
		this.sizeRemainPer = sizeRemainPer;
	}
	
}
