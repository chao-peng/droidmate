package org.droidmate.deviceInterface.exploration

import java.io.Serializable

/** this annotation is used by the exploration model to easily determine the order (by [ordinal]) and header names for properties to be persisted */
@Target( AnnotationTarget.PROPERTY) annotation class Persistent(val header: String, val ordinal: Int)

interface UiElementPropertiesI : Serializable {		//FIXME load/create properties for these properties

	fun copy(): UiElementPropertiesI {
		TODO("if necessary should be implemented by instantiating class")
	}

	/** -----------------------------------------------------------
	 * (potentially) used for default unique id computation
	 * ------------------------------------------------------------ */

	/** true if this element is part of the device (Soft-) Keyboard window */
	@property:Persistent("Is Keyboard-Element", 42)
	val isKeyboard: Boolean

	@property:Persistent("Displayed Text", 5)
	val text: String

	@property:Persistent("Alternative Text", 6)
	val contentDesc: String

	@property:Persistent("Resource Id", 42)
	val resourceId: String

	@property:Persistent("UI Class", 2)
	val className: String

	@property:Persistent("Package Name", 42)
	val packageName: String

	@property:Persistent("Text Input-Field", 42)
	val isInputField: Boolean

	@property:Persistent("Password-Field", 42)
	val isPassword: Boolean

	/** -----------------------------------------------------------
	 * used to determine this elements configuration
	 * ------------------------------------------------------------ */

	/**
	 * These are the visible (outer) boundaries (including all visible children and not only the unique covered area) for this element.
	 * It is always empty if this element is not [enabled] or is not [definedAsVisible].
	 * It can be used to determine the elements image if a screenshot exists or if an descendant is outside of this bounds,
	 * to determine the area on which a swipe action can be executed to "navigate to" that "invisible" element.
	 */
	@property:Persistent("Visible Boundaries", 19)  // persist to know whether it is (completely) visible
	val visibleBounds: Rectangle

	/** REMARK: the boundaries may lay outside of the screen boundaries, if the element is (partially) invisible.
	 * This is necessary to compute potential scroll operations to navigate to this element (get it into the definedAsVisible area) */
	@property:Persistent("Defined Boundaries", 20)
	val boundaries: Rectangle // we want this to be able to "offline" determine e.g. nearby labels

	/** True if this (checkbox) element is checked, false if not and null if it is not checkable (probably no checkbox) */
	@property:Persistent("Is Clickable", 42)
	val clickable: Boolean

	@property:Persistent("Checkable", 10)
	val checked: Boolean?

	@property:Persistent("Is Long-Clickable", 42)
	val longClickable: Boolean

	/** True if this element is focused, false if not and null if it cannot be focused */
	@property:Persistent("Focus", 42)
	val focused: Boolean?

	@property:Persistent("Selected", 42)
	val selected: Boolean

	@property:Persistent("Is Scrollable", 42)
	val scrollable: Boolean

	/** useful meta information either we use xpath or hash id in configuration id, to ensure that elements within the same page
	 * which ONLY differ their ui-hierarchy position are not reduced to the same element.
	 * This is in particular the case for layout container elements which are identical except for their xpath / set of descendants
	 */
	@property:Persistent("Xpath", 42)
	val xpath: String

	/** used internally to re-identify elements between device and pc or to reconstruct parent/child relations within a state
	 * (computed as hash code of the elements (customized by +windowLayer) unique xpath) */
	@property:Persistent("Internal Id", 42)
	val idHash: Int // internally computed on device

	@property:Persistent("Internal ParentId", 42)
	val parentHash: Int

	@property:Persistent("Internal Child-Ids", 42)
	val childHashes: List<Int>

	/** This is true if the UiAutomator reported this element as visible, it's probably only useful for the propertyId computation.
	 * During exploration you should use [visibleBounds] instead to determine visibility. */
	@property:Persistent("Can be Visible", 42)
	val definedAsVisible: Boolean

	/** This is true if the UiAutomator reported this element as enabled, it's probably only useful for the propertyId computation.
	 * During exploration you should use [visibleBounds] instead to determine visibility. */
	@property:Persistent("Is Enabled", 42)
	val enabled: Boolean

	val imgId: Int  // to support delayed image compression/transfer we have to do the imgId computation on device based on the captured bitmap

	/** -----------------------------------------------------------
	 * properties that should be only necessary during exploration for target selection
	 * ------------------------------------------------------------ */

	/** window and UiElement overlays are analyzed to determine if this element is accessible (partially on top)
	 * ore hidden behind other elements (like menu bars).
	 * If [hasUncoveredArea] is true these boundaries are uniquely covered by this UI element otherwise it may contain definedAsVisible child coordinates
	 */
	@property:Persistent("Visible Areas", 42)
	val visibleAreas: List<Rectangle>

	@property:Persistent("Covers Unique Area", 19)
	val hasUncoveredArea: Boolean

	/** -----------------------------------------------------------
	 * !!! These properties are not persisted and should NOT be actively used for anything but debugging information !!!
	 * ------------------------------------------------------------ */

	val metaInfo: List<String>

	companion object {
		// necessary for TCP communication, otherwise it would be computed by the class hash which may cause de-/serialization errors
		const val serialVersionUID: Long = 5205083142890068067//		@JvmStatic
	}

}
