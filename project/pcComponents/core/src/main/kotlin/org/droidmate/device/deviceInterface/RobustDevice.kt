// DroidMate, an automated execution generator for Android apps.
// Copyright (C) 2012-2018. Saarland University
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program.  If not, see <http://www.gnu.org/licenses/>.
//
// Current Maintainers:
// Nataniel Borges Jr. <nataniel dot borges at cispa dot saarland>
// Jenny Hotzkow <jenny dot hotzkow at cispa dot saarland>
//
// Former Maintainers:
// Konrad Jamrozik <jamrozik at st dot cs dot uni-saarland dot de>
//
// web: www.droidmate.org

package org.droidmate.device.deviceInterface

import org.droidmate.configuration.ConfigProperties
import org.droidmate.configuration.ConfigurationWrapper
import org.droidmate.device.AllDeviceAttemptsExhaustedException
import org.droidmate.device.IAndroidDevice
import org.droidmate.device.TcpServerUnreachableException
import org.droidmate.device.android_sdk.IApk
import org.droidmate.device.android_sdk.NoAndroidDevicesAvailableException
import org.droidmate.device.error.DeviceException
import org.droidmate.device.logcat.IApiLogcatMessage
import org.droidmate.device.logcat.IDeviceMessagesReader
import org.droidmate.deviceInterface.communication.TimeFormattedLogMessageI
import org.droidmate.deviceInterface.exploration.ActionType
import org.droidmate.deviceInterface.exploration.DeviceResponse
import org.droidmate.deviceInterface.exploration.ExplorationAction
import org.droidmate.deviceInterface.exploration.GlobalAction
import org.droidmate.exploration.actions.click
import org.droidmate.logging.Markers
import org.droidmate.misc.Utils
import org.slf4j.LoggerFactory
import java.lang.Thread.sleep
import java.nio.file.Path
import java.time.LocalDateTime

// TODO Very confusing method chain. Simplify
class RobustDevice : IRobustDevice {
	override fun pullFile(fileName: String, dstPath: Path, srcPath: String) =
		this.device.pullFile(fileName,dstPath,srcPath)  // pull may fail because we try to fetch images which were not possible to capture -> no retry here

	override fun removeFile(fileName: String, srcPath: String) {
		Utils.retryOnException(
				{ this.device.removeFile(fileName,srcPath) },
				{},
				DeviceException::class,
				deviceOperationAttempts,
				deviceOperationDelay,
				"device.removeFile()"
		)	}

	companion object {
		private val log by lazy { LoggerFactory.getLogger(RobustDevice::class.java) }
	}

	private val device: IAndroidDevice

	private val cfg: ConfigurationWrapper

	private val messagesReader: IDeviceMessagesReader

	private val checkAppIsRunningRetryAttempts: Int
	private val checkAppIsRunningRetryDelay: Int

	private val stopAppRetryAttempts: Int
	private val stopAppSuccessCheckDelay: Int

	private val checkDeviceAvailableAfterRebootAttempts: Int
	private val checkDeviceAvailableAfterRebootFirstDelay: Int
	private val checkDeviceAvailableAfterRebootLaterDelays: Int

	private val waitForCanRebootDelay: Int

    private val deviceOperationAttempts: Int
    private val deviceOperationDelay: Int

	private val ensureHomeScreenIsDisplayedAttempts = 3

	constructor(device: IAndroidDevice, cfg: ConfigurationWrapper) : this(device,
			cfg,
			cfg[ConfigProperties.DeviceCommunication.checkAppIsRunningRetryAttempts],
			cfg[ConfigProperties.DeviceCommunication.checkAppIsRunningRetryDelay],
			cfg[ConfigProperties.DeviceCommunication.stopAppRetryAttempts],
			cfg[ConfigProperties.DeviceCommunication.stopAppSuccessCheckDelay],
			cfg[ConfigProperties.DeviceCommunication.checkDeviceAvailableAfterRebootAttempts],
			cfg[ConfigProperties.DeviceCommunication.checkDeviceAvailableAfterRebootFirstDelay],
			cfg[ConfigProperties.DeviceCommunication.checkDeviceAvailableAfterRebootLaterDelays],
			cfg[ConfigProperties.DeviceCommunication.waitForCanRebootDelay],
            cfg[ConfigProperties.DeviceCommunication.deviceOperationAttempts],
            cfg[ConfigProperties.DeviceCommunication.deviceOperationDelay],
			cfg[ConfigProperties.ApiMonitorServer.monitorUseLogcat])

	constructor(device: IAndroidDevice,
	            cfg: ConfigurationWrapper,
	            checkAppIsRunningRetryAttempts: Int,
	            checkAppIsRunningRetryDelay: Int,
	            stopAppRetryAttempts: Int,
	            stopAppSuccessCheckDelay: Int,
	            checkDeviceAvailableAfterRebootAttempts: Int,
	            checkDeviceAvailableAfterRebootFirstDelay: Int,
	            checkDeviceAvailableAfterRebootLaterDelays: Int,
	            waitForCanRebootDelay: Int,
	            deviceOperationAttempts: Int,
	            deviceOperationDelay: Int,
	            monitorUseLogcat: Boolean) {
		this.device = device
		this.cfg = cfg
		this.messagesReader = DeviceMessagesReader(device, monitorUseLogcat)
		this.checkAppIsRunningRetryAttempts = checkAppIsRunningRetryAttempts
		this.checkAppIsRunningRetryDelay = checkAppIsRunningRetryDelay
		this.stopAppRetryAttempts = stopAppRetryAttempts
		this.stopAppSuccessCheckDelay = stopAppSuccessCheckDelay
		this.checkDeviceAvailableAfterRebootAttempts = checkDeviceAvailableAfterRebootAttempts
		this.checkDeviceAvailableAfterRebootFirstDelay = checkDeviceAvailableAfterRebootFirstDelay
		this.checkDeviceAvailableAfterRebootLaterDelays = checkDeviceAvailableAfterRebootLaterDelays
		this.waitForCanRebootDelay = waitForCanRebootDelay
        this.deviceOperationAttempts = deviceOperationAttempts
        this.deviceOperationDelay = deviceOperationDelay

		assert(checkAppIsRunningRetryAttempts >= 1)
		assert(stopAppRetryAttempts >= 1)
		assert(checkDeviceAvailableAfterRebootAttempts >= 1)
		assert(deviceOperationAttempts >= 1)

		assert(checkAppIsRunningRetryDelay >= 0)
		assert(stopAppSuccessCheckDelay >= 0)
		assert(checkDeviceAvailableAfterRebootFirstDelay >= 0)
		assert(checkDeviceAvailableAfterRebootLaterDelays >= 0)
		assert(waitForCanRebootDelay >= 0)
		assert(deviceOperationDelay >= 0)
	}

	override fun uninstallApk(apkPackageName: String, ignoreFailure: Boolean) {
		if (ignoreFailure)
			device.uninstallApk(apkPackageName, ignoreFailure)
		else {
			try {
				device.uninstallApk(apkPackageName, ignoreFailure)
			} catch (e: DeviceException) {
				val appIsInstalled: Boolean
				try {
					appIsInstalled = device.hasPackageInstalled(apkPackageName)
				} catch (e2: DeviceException) {
					throw DeviceException("Uninstalling of $apkPackageName failed with exception E1: '$e'. " +
							"Tried to check if the app that was to be uninstalled is still installed, but that also resulted in exception, E2. " +
							"Discarding E1 and throwing an exception having as a cause E2", e2)
				}

				if (appIsInstalled)
					throw DeviceException("Uninstalling of $apkPackageName threw an exception (given as cause of this exception) and the app is indeed still installed.", e)
				else {
					log.debug("Uninstalling of $apkPackageName threw an exception, but the app is no longer installed. Note: this situation has proven to make the uiautomator be unable to dump window hierarchy. Discarding the exception '$e', resetting connection to the device and continuing.")
					// Doing .rebootAndRestoreConnection() just hangs the emulator: http://stackoverflow.com/questions/9241667/how-to-reboot-emulator-to-test-action-boot-completed
					this.closeConnection()
					this.setupConnection()
				}
			}
		}
	}

	override fun setupConnection() {
		rebootIfNecessary("device.setupConnection()", true) { this.device.setupConnection() }
	}

	override fun clearPackage(apkPackageName: String) {
		// Clearing package has to happen more than once, because sometimes after cleaning suddenly the ActivityManager restarts
		// one of the activities of the app.
		Utils.retryOnFalse({

			Utils.retryOnException({ device.clearPackage(apkPackageName) },
					{},
					DeviceException::class,
					deviceOperationAttempts,
					deviceOperationDelay,
					"clearPackage")

			// Sleep here to give the device some time to stop all the processes belonging to the cleared package before checking
			// if indeed all of them have been stopped.
			sleep(this.stopAppSuccessCheckDelay.toLong())

			!this.getAppIsRunningRebootingIfNecessary(apkPackageName)

		},
				this.stopAppRetryAttempts,
				/* Retry timeout. Zero, because after seeing the app didn't stop, we immediately clear package again. */
				0)
	}

	private fun DeviceResponse.isSelectAHomeAppDialogBox(): Boolean =
			widgets.any { it.text == "Just once" } &&
					widgets.any { it.text == "Select a Home app" }

	private fun DeviceResponse.isUseLauncherAsHomeDialogBox(): Boolean =
			widgets.any { it.text == "Use Launcher as Home" } &&
					widgets.any { it.text == "Just once" } &&
					widgets.any { it.text == "Always" }


	override fun ensureHomeScreenIsDisplayed(): DeviceResponse {
		var guiSnapshot = this.getExplorableGuiSnapshot()
		if (guiSnapshot.isHomeScreen)
			return guiSnapshot

		Utils.retryOnFalse({
			if (!guiSnapshot.isHomeScreen) {
				guiSnapshot = when {  //FIXME what are these, are they even still useful?
					guiSnapshot.isSelectAHomeAppDialogBox() ->
						closeSelectAHomeAppDialogBox(guiSnapshot)
					guiSnapshot.isUseLauncherAsHomeDialogBox() ->
						closeUseLauncherAsHomeDialogBox(guiSnapshot)
					else -> {
						perform(GlobalAction(ActionType.PressHome))
					}
				}
			}

			guiSnapshot.isHomeScreen
		},
				ensureHomeScreenIsDisplayedAttempts, /* timeout */ 0)

		if (!guiSnapshot.isHomeScreen) {
			throw DeviceException("Failed to ensure home screen is displayed. " +
					"Pressing 'home' button didn't help. Instead, ended with GUI state of: $guiSnapshot.\n" +
					"Full window hierarchy dump:\n" +
					guiSnapshot.windowHierarchyDump)
		}

		return guiSnapshot
	}

	private fun closeSelectAHomeAppDialogBox(snapshot: DeviceResponse): DeviceResponse {
		val launcherWidget = snapshot.widgets.single { it.text == "Launcher" }
		perform(launcherWidget.click())

		var guiSnapshot = this.getExplorableGuiSnapshot()
		if (guiSnapshot.isSelectAHomeAppDialogBox()) {
			val justOnceWidget = guiSnapshot.widgets.single { it.text == "Just once" }
			perform(justOnceWidget.click())
			guiSnapshot = this.getExplorableGuiSnapshot()
		}
		assert(!guiSnapshot.isSelectAHomeAppDialogBox())

		return guiSnapshot
	}

	private fun closeUseLauncherAsHomeDialogBox(snapshot: DeviceResponse): DeviceResponse {
		val justOnceWidget = snapshot.widgets.single { it.text == "Just once" }
		perform(justOnceWidget.click())

		val guiSnapshot = this.getExplorableGuiSnapshot()
		assert(!guiSnapshot.isUseLauncherAsHomeDialogBox())
		return guiSnapshot
	}

	override fun perform(action: ExplorationAction): DeviceResponse {
		return Utils.retryOnFalse({
					Utils.retryOnException(
							{
								this.device.perform(action)
							},
							{ this.restartUiaDaemon(false) },
							DeviceException::class,
							deviceOperationAttempts,
							0,
							"device.perform(action:$action)"
					)
				},
				{ it.isSuccessful },
				deviceOperationAttempts,
				deviceOperationDelay)
	}

	override fun appIsNotRunning(apk: IApk): Boolean {
		return Utils.retryOnFalse({ !this.getAppIsRunningRebootingIfNecessary(apk.packageName) },
				checkAppIsRunningRetryAttempts,
				checkAppIsRunningRetryDelay)
	}

	@Throws(DeviceException::class)
	private fun getAppIsRunningRebootingIfNecessary(packageName: String): Boolean = rebootIfNecessary("device.appIsRunning(packageName:$packageName)", true) { this.device.appIsRunning(packageName) }

	@Throws(DeviceException::class)
	private fun getExplorableGuiSnapshot(): DeviceResponse {
		var guiSnapshot = this.getRetryValidGuiSnapshotRebootingIfNecessary()
		guiSnapshot = closeANRIfNecessary(guiSnapshot)
		return guiSnapshot
	}

	private fun DeviceResponse.isAppHasStoppedDialogBox(): Boolean =
			widgets.any { it.resourceId == "android:id/aerr_close" } &&
					widgets.any { it.resourceId == "android:id/aerr_wait" }

	@Throws(DeviceException::class)
	private fun closeANRIfNecessary(guiSnapshot: DeviceResponse): DeviceResponse {
		if (!guiSnapshot.isAppHasStoppedDialogBox())
			return guiSnapshot

		assert(guiSnapshot.isAppHasStoppedDialogBox())
		var targetWidget = guiSnapshot.widgets.firstOrNull { it.text == "OK" } ?:
			guiSnapshot.widgets.first { it.resourceId == "android:id/aerr_close" }
		assert(targetWidget.enabled)
		log.debug("ANR encountered")

		var out = guiSnapshot

		Utils.retryOnFalse({

			assert(targetWidget.enabled)
			device.perform(targetWidget.click())
			out = this.getRetryValidGuiSnapshotRebootingIfNecessary()

			if (out.isAppHasStoppedDialogBox()) {
				targetWidget = guiSnapshot.widgets.firstOrNull { it.text == "OK" } ?:
						guiSnapshot.widgets.first { it.resourceId == "android:id/aerr_close" }
				log.debug("ANR encountered - again. Failed to properly close it even though its OK widget was enabled.")

				false
			} else
				true
		},
				deviceOperationAttempts,
				deviceOperationDelay)

		return out
	}

	@Throws(DeviceException::class)
	private fun getRetryValidGuiSnapshotRebootingIfNecessary(): DeviceResponse = rebootIfNecessary("device.getRetryValidGuiSnapshot()", true) { this.getRetryValidGuiSnapshot() }

	@Throws(DeviceException::class)
	private fun getRetryValidGuiSnapshot(): DeviceResponse {
		try {
			return Utils.retryOnException(
					{ getValidGuiSnapshot() },
					{ restartUiaDaemon(false) },
					DeviceException::class,
					deviceOperationAttempts,
					deviceOperationDelay,
					"getValidGuiSnapshot")
		} catch (e: DeviceException) {
			throw AllDeviceAttemptsExhaustedException("All attempts at getting valid GUI snapshot failed", e)
		}
	}

	@Throws(DeviceException::class)
	private fun getValidGuiSnapshot(): DeviceResponse {
		// the rebootIfNecessary will reboot on TcpServerUnreachable
		return rebootIfNecessary("device.getGuiSnapshot()", true) {
			perform(GlobalAction(ActionType.FetchGUI))
		}
	}

	@Throws(DeviceException::class)
	private fun <T> rebootIfNecessary(description: String, makeSecondAttempt: Boolean, operationOnDevice: () -> T): T {
		try {
			return operationOnDevice.invoke()
		} catch (e: Exception) {
			if ((e !is TcpServerUnreachableException) and (e !is AllDeviceAttemptsExhaustedException))
				throw e

			log.warn(Markers.appHealth, "! Attempt to execute '$description' threw an exception: $e. " +
					(if (makeSecondAttempt)
						"Reconnecting adb, rebooting the device and trying again."
					else
						"Reconnecting adb, rebooting the device and continuing."))

			// Removed by Nataniel
			// This is not feasible when using the device farm, upon restart of the ADB server the connection
			// to the device is lost and it's assigned a new, random port, which doesn't allow automatic reconnection.
			//this.reconnectAdbDiscardingException("Call to reconnectAdb() just before call to rebootAndRestoreConnection() " +
			//        "failed with: %s. Discarding the exception and continuing wih rebooting.")
			//this.reinstallUiAutomatorDaemon()
			this.rebootAndRestoreConnection()

			if (makeSecondAttempt) {
				log.info("Reconnected adb and rebooted successfully. Making second and final attempt at executing '$description'")
				try {
					val out = operationOnDevice()
					log.info("Second attempt at executing '$description' completed successfully.")
					return out
				} catch (e2: Exception) {
					if ((e2 !is TcpServerUnreachableException) and (e2 !is AllDeviceAttemptsExhaustedException))
						throw e2
					log.warn(Markers.appHealth, "! Second attempt to execute '$description' threw an exception: $e2. " +
							"Giving up and rethrowing.")
					throw e2
				}
			} else {
				throw e
			}
		}
	}

	override fun reboot() {
		if (this.device.isAvailable()) {
			log.trace("Device is available for rebooting.")
		} else {
			log.trace("Device not yet available for a reboot. Waiting $waitForCanRebootDelay milliseconds. If the device still won't be available, " +
					"assuming it cannot be reached at all.")

			sleep(this.waitForCanRebootDelay.toLong())

			if (this.device.isAvailable())
				log.trace("Device can be rebooted after the wait.")
			else
				throw DeviceException("Device is not available for a reboot, even after the wait. Requesting to stop further apk explorations.", true)
		}

		log.trace("Rebooting.")
		this.device.reboot()

		sleep(this.checkDeviceAvailableAfterRebootFirstDelay.toLong())
		// WISH use "adb wait-for-device"
		val rebootResult = Utils.retryOnFalse({
			val out = this.device.isAvailable()
			if (!out)
				log.trace("Device not yet available after rebooting, waiting $checkDeviceAvailableAfterRebootLaterDelays milliseconds and retrying")
			out
		},
				checkDeviceAvailableAfterRebootAttempts,
				checkDeviceAvailableAfterRebootLaterDelays)

		if (rebootResult) {
			assert(this.device.isAvailable())
			log.trace("Reboot completed successfully.")
		} else {
			assert(!this.device.isAvailable())
			throw DeviceException("Device is not available after a reboot. Requesting to stop further apk explorations.", true)
		}

		assert(!this.device.uiaDaemonClientThreadIsAlive())
	}

	override fun rebootAndRestoreConnection() {
		// Removed by Nataniel
		// This is not feasible when using the device farm, upon restart of the ADB server the connection
		// to the device is lost and it's assigned a new, random port, which doesn't allow automatic reconnection.
	}

	override fun getAndClearCurrentApiLogs(): List<IApiLogcatMessage> {
        return rebootIfNecessary("messagesReader.getAndClearCurrentApiLogs()", true) { this.messagesReader.getAndClearCurrentApiLogs() }
    }

	override fun closeConnection() {
		rebootIfNecessary("closeConnection()", true) { this.device.closeConnection() }
	}

	override fun toString(): String = "robust-" + this.device.toString()

	override fun pushFile(jar: Path) {
        Utils.retryOnException(
                { this.device.pushFile(jar) },
                {},
                DeviceException::class,
                deviceOperationAttempts,
                deviceOperationDelay,
                "device.pushFile(jar:$jar)"
        )
	}

	override fun pushFile(jar: Path, targetFileName: String) {
        Utils.retryOnException(
                { this.device.pushFile(jar, targetFileName) },
                {},
                DeviceException::class,
                deviceOperationAttempts,
                deviceOperationDelay,
                "device.pushFile(jar:$jar, targetFileName:$targetFileName)"
        )
	}

	override fun removeJar(jar: Path) {
        Utils.retryOnException(
                { this.device.removeJar(jar) },
                {},
                DeviceException::class,
                deviceOperationAttempts,
                deviceOperationDelay,
                "device.removeJar(jar:$jar)"
        )
	}

	override fun installApk(apk: Path) {
        Utils.retryOnException(
                { this.device.installApk(apk) },
                {},
                DeviceException::class,
                deviceOperationAttempts,
                deviceOperationDelay,
                "device.installApk(apk:$apk)"
        )
	}

	override fun installApk(apk: IApk) {
        Utils.retryOnException(
                { this.device.installApk(apk) },
                {},
                DeviceException::class,
                deviceOperationAttempts,
                deviceOperationDelay,
                "device.installApk(apk:$apk)"
        )
	}

	override fun isApkInstalled(apkPackageName: String): Boolean {
        return Utils.retryOnException(
                { this.device.isApkInstalled(apkPackageName) },
                {},
                DeviceException::class,
                deviceOperationAttempts,
                deviceOperationDelay,
                "device.isApkInstalled(apkPackageName:$apkPackageName)"
        )
	}

	override fun closeMonitorServers() {
        Utils.retryOnException(
                { this.device.closeMonitorServers() },
                {},
                DeviceException::class,
                deviceOperationAttempts,
                deviceOperationDelay,
                "device.closeMonitorServers()"
        )
	}

	override fun appProcessIsRunning(appPackageName: String): Boolean {
        return Utils.retryOnException(
                { this.device.appProcessIsRunning(appPackageName) },
                {},
                DeviceException::class,
                deviceOperationAttempts,
                deviceOperationDelay,
                "device.appProcessIsRunning(appPackageName:$appPackageName)"
        )
    }

	override fun clearLogcat() {
        Utils.retryOnException(
                { this.device.clearLogcat() },
                {},
                DeviceException::class,
                deviceOperationAttempts,
                deviceOperationDelay,
                "device.clearLogcat()"
        )
	}

    override fun stopUiaDaemon(uiaDaemonThreadIsNull: Boolean) {
        Utils.retryOnException(
                {
                    try {
                        this.device.stopUiaDaemon(uiaDaemonThreadIsNull)
                    } catch (e: TcpServerUnreachableException) {
                        log.warn("Unable to issue stop command to UIAutomator. Assuming it's no longer running.")
                    } // retry on other exceptions
                },
                {},
                DeviceException::class,
                deviceOperationAttempts,
                deviceOperationDelay,
                "device.stopUiaDaemon"
        )
    }

    override fun isAvailable(): Boolean {
        return Utils.retryOnException(
                {
                    try {
                        this.device.isAvailable()
                    } catch (ignored: NoAndroidDevicesAvailableException) {
                        false
                    } // retry on other exceptions
                },
                {},
                DeviceException::class,
                deviceOperationAttempts,
                deviceOperationDelay,
                "device.isAvailable()"
        )
    }

	override fun uiaDaemonClientThreadIsAlive(): Boolean {
        return Utils.retryOnException(
                { this.device.uiaDaemonClientThreadIsAlive() },
                {},
                DeviceException::class,
                deviceOperationAttempts,
                deviceOperationDelay,
                "device.uiaDaemonClientThreadIsAlive()"
        )
    }

	override fun restartUiaDaemon(uiaDaemonThreadIsNull: Boolean) {
		if (this.uiaDaemonIsRunning()) {
			this.stopUiaDaemon(uiaDaemonThreadIsNull)
		}
		this.startUiaDaemon()
	}

	override fun startUiaDaemon() {
        Utils.retryOnException(
                { this.device.startUiaDaemon() },
                {},
                DeviceException::class,
                deviceOperationAttempts,
                deviceOperationDelay,
                "device.startUiaDaemon()"
        )
	}

	override fun removeLogcatLogFile() {
        Utils.retryOnException(
                { this.device.removeLogcatLogFile() },
                {},
                DeviceException::class,
                deviceOperationAttempts,
                deviceOperationDelay,
                "device.removeLogcatLogFile()"
        )
	}

	override fun pullLogcatLogFile() {
        Utils.retryOnException(
                { this.device.pullLogcatLogFile() },
                {},
                DeviceException::class,
                deviceOperationAttempts,
                deviceOperationDelay,
                "device.pullLogcatLogFile()"
        )
	}

	override fun reinstallUiAutomatorDaemon() {
        Utils.retryOnException(
                { this.device.reinstallUiAutomatorDaemon() },
                {},
                DeviceException::class,
                deviceOperationAttempts,
                deviceOperationDelay,
                "device.reinstallUiautomatorDaemon()"
        )
	}

	override fun pushMonitorJar() {
        Utils.retryOnException(
                { this.device.pushMonitorJar() },
                {},
                DeviceException::class,
                deviceOperationAttempts,
                deviceOperationDelay,
                "device.pushMonitorJar()"
        )
	}

	override fun reconnectAdb() {
        Utils.retryOnException(
                { this.device.reconnectAdb() },
                {},
                DeviceException::class,
                deviceOperationAttempts,
                deviceOperationDelay,
                "device.reconnectAdb()"
        )
	}

	override fun executeAdbCommand(command: String, successfulOutput: String, commandDescription: String) {
        Utils.retryOnException(
                { this.device.executeAdbCommand(command, successfulOutput, commandDescription) },
                {},
                DeviceException::class,
                deviceOperationAttempts,
                deviceOperationDelay,
                "device.executeAdbCommand(command:$command, successfulOutput:$successfulOutput, commandDescription:$commandDescription)"
        )
	}

	override fun uiaDaemonIsRunning(): Boolean {
		return try {
			this.device.uiaDaemonIsRunning()
		} catch (e: Exception) {
			log.warn("Could not check if UIAutomator daemon is running. Assuming it is not and proceeding")

			false
		}
	}

	override fun isPackageInstalled(packageName: String): Boolean {
        return Utils.retryOnException(
                { this.device.isPackageInstalled(packageName) },
                {},
                DeviceException::class,
                deviceOperationAttempts,
                deviceOperationDelay,
                "device.isPackageInstalled(packageName:$packageName)"
        )
    }

	override fun hasPackageInstalled(packageName: String): Boolean {
        return Utils.retryOnException(
                { this.device.hasPackageInstalled(packageName) },
                {},
                DeviceException::class,
                deviceOperationAttempts,
                deviceOperationDelay,
                "device.hasPackageInstalled(packageName:$packageName)"
        )
    }

	override fun readLogcatMessages(messageTag: String): List<TimeFormattedLogMessageI> {
        return Utils.retryOnException(
                { this.device.readLogcatMessages(messageTag) },
                {},
                DeviceException::class,
                deviceOperationAttempts,
                deviceOperationDelay,
                "device.readLogcatMessages(messageTag:$messageTag)"
        )
    }

	override fun readStatements(): List<List<String>> {
		return Utils.retryOnException(
			{ this.device.readStatements() },
			{},
			DeviceException::class,
			deviceOperationAttempts,
			deviceOperationDelay,
			"device.readStatements()"
		)
	}

	override fun waitForLogcatMessages(messageTag: String, minMessagesCount: Int, waitTimeout: Int, queryDelay: Int): List<TimeFormattedLogMessageI> {
        return Utils.retryOnException(
                { this.device.waitForLogcatMessages(messageTag, minMessagesCount, waitTimeout, queryDelay) },
                {},
                DeviceException::class,
                deviceOperationAttempts,
                deviceOperationDelay,
                "device.waitForLogcatMessages(messageTag:$messageTag, minMessagesCount:$minMessagesCount, waitTimeout:$waitTimeout, queryDelay:$queryDelay)"
        )
    }

	override fun readAndClearMonitorTcpMessages(): List<List<String>> {
        return Utils.retryOnException(
                { this.device.readAndClearMonitorTcpMessages() },
                {},
                DeviceException::class,
                deviceOperationAttempts,
                deviceOperationDelay,
                "device.readAndClearMonitorTcpMessages()"
        )
    }

	override fun getCurrentTime(): LocalDateTime {
        return Utils.retryOnException(
                { this.device.getCurrentTime() },
                {},
                DeviceException::class,
                deviceOperationAttempts,
                deviceOperationDelay,
                "device.getCurrentTime()"
        )
    }

	override fun anyMonitorIsReachable(): Boolean {
        return Utils.retryOnException(
                { this.device.anyMonitorIsReachable() },
                {},
                DeviceException::class,
                deviceOperationAttempts,
                deviceOperationDelay,
                "device.anyMonitorIsReachable()"
        )
    }

	override fun appIsRunning(appPackageName: String): Boolean {
        return Utils.retryOnException(
                { this.device.appIsRunning(appPackageName) },
                {},
                DeviceException::class,
                deviceOperationAttempts,
                deviceOperationDelay,
                "device.appIsRunning(appPackageName:$appPackageName)"
        )
    }

	override fun resetTimeSync() {
        Utils.retryOnException(
                { this.messagesReader.resetTimeSync() },
                {},
                DeviceException::class,
                deviceOperationAttempts,
                deviceOperationDelay,
                "messagesReader.resetTimeSync()"
        )
    }
}
