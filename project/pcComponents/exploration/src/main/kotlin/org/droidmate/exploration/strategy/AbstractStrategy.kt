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

package org.droidmate.exploration.strategy

import org.droidmate.deviceInterface.exploration.ExplorationAction
import org.droidmate.explorationModel.interaction.Interaction
import org.droidmate.explorationModel.interaction.State
import org.droidmate.exploration.ExplorationContext
import org.droidmate.exploration.strategy.widget.ExplorationStrategy
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Abstract class which contains common functionality for all exploration
 * strategies inside the strategy pool. Recommended to override this class
 * instead of the interface.

 * @author Nataniel P. Borges Jr.
 */
abstract class AbstractStrategy : ISelectableExplorationStrategy {
	override val uniqueStrategyName: String = javaClass.simpleName
	/**
	 * if this parameter is true we can invoke a strategy without registering it, this should be only used for internal
	 * default strategies from our selector functions like Reset,Back etc.
	 */
	override val noContext: Boolean = false

	/**
	 * Internal context of the strategy. Synchronized with exploration context upon initialization.
	 */
	protected lateinit var eContext: ExplorationContext<*, *, *>
		private set

	protected val currentState get() = eContext.getCurrentState()

	/**
	 * Check if this is the first time an action will be performed ([eContext] is empty)
	 * @return If this is the first exploration action or not
	 */
	internal fun firstDecisionIsBeingMade(): Boolean {
		return eContext.isEmpty()
	}

	/**
	 * Check if last performed action in the [eContext] was to reset the app
	 * @return If the last action was a reset
	 */
	internal fun lastAction(): Interaction<*> {
		return this.eContext.getLastAction()
	}

	/**
	 * Get action before the last one.
	 *
	 * Used by some strategies (ex: Terminate and Back) to prevent loops (ex: Reset -> Back -> Reset -> Back)
	 */
	suspend fun getSecondLastAction(): Interaction<*> {
		if (this.eContext.getSize() < 2)
			return eContext.emptyAction

		return this.eContext.explorationTrace.P_getActions().dropLast(1).last()
	}

	override fun initialize(memory: ExplorationContext<*, *, *>) {
		this.eContext = memory
	}

	override suspend fun decide(): ExplorationAction {
		val action = this.internalDecide()

		return action
	}

	override fun equals(other: Any?): Boolean {
		return (other != null) && this.javaClass == other.javaClass
	}

	override fun hashCode(): Int {
		return this.javaClass.hashCode()
	}
	override fun toString() = uniqueStrategyName

	/**
	 * Selects an action to be executed based on the [current widget context][currentState]
	 */
	abstract suspend fun internalDecide(): ExplorationAction

	companion object {
		val logger: Logger by lazy { LoggerFactory.getLogger(ExplorationStrategy::class.java) }

		val VALID_WIDGETS = ResourceManager.getResourceAsStringList("validWidgets.txt")
	}
}
