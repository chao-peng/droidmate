// DroidMate, an automated execution generator for Android apps.
// Copyright (C) 2012-2016 Konrad Jamrozik
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
package org.droidmate.test_tools.exceptions

class ExceptionSpec constructor(override val methodName: String,
                                override val packageName : String = "",
                                override val callIndex : Int = 1,
                                override val throwsEx : Boolean = true,
                                override val exceptionalReturnBool : Boolean? = null,
                                private val throwsAssertionError : Boolean = false): IExceptionSpec
{
  companion object {
    private const val serialVersionUID: Long = 1
  }

  init {
      assert(this.throwsEx == (this.exceptionalReturnBool == null))
      assert(!this.throwsAssertionError || this.throwsEx)
  }

    override fun matches(methodName: String, packageName: String, callIndex: Int): Boolean {
        if (this.methodName == methodName && (this.packageName in arrayListOf("", packageName)) && this.callIndex == callIndex)
            return true
        return false
    }

    override fun throwEx() {
        assert(this.exceptionalReturnBool == null)
        if (this.throwsAssertionError)
            throw TestAssertionError (this)
        else
            throw TestDeviceException (this)
    }

    override fun toString(): String {
        return "mthd: $methodName pkg: $packageName idx: $callIndex throw: $throwsEx"
    }
}
