// DroidMate, an automated execution generator for Android apps.
// Copyright (C) 2012-2018 Konrad Jamrozik
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
package org.droidmate.exploration.strategy

import org.droidmate.device.datatypes.IWidget
import org.droidmate.misc.isEquivalentIgnoreLocation
import org.droidmate.misc.uniqueString
import java.util.*
import kotlin.collections.ArrayList

/**
 * Implementation of a widget that must be located during exploration
 *
 * @constructor Creates a new taret given a widget and a set of dependencies. The list of dependencies can be empty
 *
 * @param dependencies Widgets which should be interacted with before the target should be acted upon
 *
 * @author Nataniel P. Borges Jr.
 */
class TargetWidget(override val widget: IWidget, vararg dependencies: ITargetWidget) : ITargetWidget {
    override val dependencies: List<ITargetWidget> = Arrays.asList(*dependencies)

    // no need to check for dependencies here, flag can only be changed is all
    // dependencies are satisfied
    override var isSatisfied: Boolean = false
        private set


    override fun isDependenciesSatisfied(): Boolean {
        return this.dependencies.none { !it.isSatisfied }
    }

    override fun satisfy(ignoreDependencies: Boolean) {
        assert(ignoreDependencies || this.isDependenciesSatisfied())

        this.isSatisfied = true
    }

    override fun hasDependencies(): Boolean {
        return !this.dependencies.isEmpty()
    }

    override fun trySatisfyWidgetOrDependency(satisfiedWidget: ITargetWidget) {
        if (this.widget.uniqueString == satisfiedWidget.widget.uniqueString)
            this.satisfy(true)
        else
            this.dependencies
                    .forEach { dependency ->
                        dependency.trySatisfyWidgetOrDependency(satisfiedWidget)
                    }
    }

    override fun getNextWidgetsCanSatisfy(): List<ITargetWidget> {
        val canSatisfy: MutableList<ITargetWidget> = ArrayList()

        if (!this.isSatisfied) {

            if (this.isDependenciesSatisfied())
                canSatisfy.add(this)
            else {
                this.dependencies.forEach { canSatisfy.addAll(it.getNextWidgetsCanSatisfy()) }
            }

        }

        return canSatisfy
    }

    override fun getTarget(widget: WidgetInfo): ITargetWidget {
        if (this.widget.isEquivalentIgnoreLocation(widget.widget))
            return this
        else {
            val foundTargets = this.dependencies.map { it.getTarget(widget) }.filter { it !is DummyTarget }

            if (foundTargets.isNotEmpty())
                return foundTargets.first()

            return DummyTarget()
        }
    }

    override fun toString(): String {
        return "Satisfied: ${this.isSatisfied}\tTarget: ${this.widget.uniqueString}"
    }

    companion object {
        @JvmStatic
        private val serialVersionUID = 1
    }
}