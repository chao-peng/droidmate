// DroidMate, an automated execution generator for Android apps.
// Copyright (C) 2012-2017 Konrad Jamrozik
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
// email: jamrozik@st.cs.uni-saarland.de
// web: www.droidmate.org
package org.droidmate.exploration.data_aggregators

import kotlinx.coroutines.experimental.CoroutineName
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.launch
import org.droidmate.android_sdk.IApk
import org.droidmate.device.datatypes.statemodel.*
import org.droidmate.device.datatypes.statemodel.features.ActionCounterMF
import org.droidmate.exploration.actions.IRunnableExplorationAction
import org.droidmate.device.datatypes.statemodel.features.IModelFeature
import java.awt.Rectangle
import java.time.LocalDateTime

class ExplorationContext @JvmOverloads constructor(override val apk: IApk,
                                                   override var explorationStartTime: LocalDateTime = LocalDateTime.MIN,
                                                   override var explorationEndTime: LocalDateTime = LocalDateTime.MIN,
                                                   override val watcher:List<IModelFeature> = listOf(ActionCounterMF()),
                                                   override val actionTrace: Trace = Trace(watcher),
                                                   override val model: Model = Model.emptyModel(ModelDumpConfig(apk.packageName))) : IExplorationLog() {

	override var deviceDisplayBounds: Rectangle? = null
	/** for debugging purpose only contains the last UiAutomator dump */
	var lastDump:String = ""

	init{
		model.addTrace(actionTrace)
		if (explorationStartTime > LocalDateTime.MIN)
			this.verify()
	}

	companion object {
		private const val serialVersionUID: Long = 1
	}

	override fun getCurrentState(): StateData = actionTrace.getCurrentState()

	override fun add(action: IRunnableExplorationAction, result: ActionResult) {
		deviceDisplayBounds = result.guiSnapshot.guiStatus.deviceDisplayBounds
		lastDump = result.guiSnapshot.windowHierarchyDump

		model.S_updateModel(result,actionTrace)
		this.also { context -> watcher.forEach { it.updateTask = async(CoroutineName(it::class.simpleName?:"update-observer")){ it.update(context) } } }
	}

	override fun dump() {
		model.P_dumpModel(model.config)
		this.also { context -> watcher.forEach { launch(CoroutineName(it::class.simpleName?:"observer-dump")) { it.dump(context) } } }
	}

	override fun areAllWidgetsExplored(): Boolean {
		return actionTrace.unexplored(getCurrentState().actionableWidgets).isNotEmpty()
	}

	override fun assertLastGuiSnapshotIsHomeOrResultIsFailure() {
		actionTrace.last()?.let {
			assert(!it.successful || getCurrentState().isHomeScreen)
		}
	}

}