package io.github.sloppytopp.homewatch.detect

/** One thing to look at during a physical sweep of a room. [how] says exactly what to do. */
data class InspectItem(val id: String, val where: String, val how: String)

/**
 * Physical-inspection checklist (the part of a professional TSCM sweep a phone cannot do for you).
 * A finished checklist means "I looked here", never "this place is clear".
 */
object Inspection {
    private val GENERAL = listOf(
        InspectItem("ceil", "Ceiling: smoke/CO detectors, light fittings, vents",
            "Look for a tiny hole or lens, a second device next to the detector, or loose/new wiring. Compare with a detector in another room."),
        InspectItem("outlets", "Outlets, USB chargers, power strips, adapters",
            "Anything plugged in that you didn't buy, a charger that is always in place with a tiny hole, or one that gets warm with nothing connected."),
        InspectItem("facing", "Objects facing the bed, sofa or desk (clocks, radios, picture frames, plants, books)",
            "Hold your eye level with each and look for a pinhole or glint. Ask: who put this here, and when?"),
        InspectItem("glint", "Lens-glint sweep",
            "Dim the room, slowly sweep a phone flashlight at eye level across shelves and walls. A small, bright, steady point reflected back can be a camera lens."),
        InspectItem("ir", "Infrared dots",
            "With the lights off, look at the room through your phone's front camera (it often shows infrared). Steady white or purple dots can be night-vision LEDs."),
        InspectItem("tv", "TVs, set-top boxes, soundbars, consoles, smart speakers",
            "Find built-in cameras and microphones. Cover or unplug the ones you don't use; check the Smart tab for what is on your network."),
        InspectItem("mirror", "Mirrors",
            "Touch a fingertip to the glass: on a normal mirror there's a gap between your finger and its reflection; on a two-way mirror they touch. Not reliable on its own."),
        InspectItem("furniture", "Furniture and soft items",
            "Look behind, under and inside shelves, drawers, cushions, tissue boxes, stuffed toys and decor. Look for anything new, moved or out of place."),
        InspectItem("router", "Wi-Fi and network",
            "Open your router's connected-devices list and look for anything you don't recognise; compare with what you own."),
        InspectItem("moved", "Things that changed",
            "Note anything that wasn't there before or has moved. Photograph it where it is."),
    )

    private val BEDROOM = listOf(InspectItem("bed", "Around the bed", "Check the headboard, bedside lamps, chargers and anything on shelves pointing at the bed."))
    private val BATHROOM = listOf(InspectItem("bath", "Towel hooks, vents, shower rod, tissue holders, hair-dryer holders",
        "Look for tiny holes or lenses at head height and above. Bathrooms are a common place for hidden cameras."))
    private val RENTAL = listOf(InspectItem("rental", "Rental or hotel extras", "Check alarm clocks, USB hubs, smoke detectors, TV sets and any device you can't explain. Unplug the ones you can."))

    private val CAR = listOf(
        InspectItem("obd", "OBD-II port under the dash", "Look for a plug-in device you don't recognise. Photograph it before touching it."),
        InspectItem("under", "Underneath and wheel wells", "With a flashlight, feel behind bumpers and inside wheel wells for small magnetic boxes."),
        InspectItem("seats", "Under and inside seats, door pockets, glove box, trunk", "Feel along seams and tuck points; check for taped-on or velcroed items."),
        InspectItem("dash", "Dashboard and mirror", "Look for a camera or box near the rear-view mirror and for extra wiring behind panels."),
        InspectItem("bags", "Bags and belongings kept in the car", "Check seams, lining and pockets for coin-sized or card-sized trackers."),
        InspectItem("ir", "Infrared dots", "With the lights off, look through your phone's front camera for steady white or purple dots."),
    )

    fun itemsFor(room: String): List<InspectItem> {
        val r = room.lowercase()
        return when {
            "car" in r || "truck" in r || "vehicle" in r || "van" in r || "suv" in r -> CAR
            else -> GENERAL + (if ("bed" in r) BEDROOM else emptyList()) +
                (if ("bath" in r || "restroom" in r || "toilet" in r) BATHROOM else emptyList()) +
                (if ("hotel" in r || "rental" in r || "airbnb" in r) RENTAL else emptyList())
        }
    }

    fun progress(room: String, done: Set<String>): Pair<Int, Int> {
        val items = itemsFor(room)
        return items.count { it.id in done } to items.size
    }
}
