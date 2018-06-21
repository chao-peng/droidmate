package org.droidmate.exploration.actions

import org.droidmate.device.android_sdk.IApk
import org.droidmate.device.deviceInterface.DeviceLogsHandler
import org.droidmate.device.deviceInterface.IRobustDevice
import org.droidmate.uiautomator_daemon.guimodel.RotateUI

class RotateUIExplorationAction(val rotation: Int) : AbstractExplorationAction(){
	companion object {
		private const val serialVersionUID: Long = 1
	}

	override fun toShortString(): String = "Rotate $rotation"

	override fun performDeviceActions(app: IApk, device: IRobustDevice) {
		log.debug("1. Assert only background API logs are present, if any.")
		val logsHandler = DeviceLogsHandler(device)
		logsHandler.readClearAndAssertOnlyBackgroundApiLogsIfAny()

		log.debug("2. Press back.")
		this.snapshot = device.perform(RotateUI(rotation))

		log.debug("3. Read and clear API logs if any, then seal logs reading.")
		logsHandler.readAndClearApiLogs()
		this.logs = logsHandler.getLogs()
	}
}