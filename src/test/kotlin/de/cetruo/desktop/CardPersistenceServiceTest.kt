package de.cetruo.desktop

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CardPersistenceServiceTest {
    @Test
    fun meaningfulCardDataRequiresAtLeastThreePopulatedFields() {
        val sparse = CardData(
            title = "",
            cost = "",
            typeLine = "",
            rarity = "",
            description = "",
            flavorText = "",
            artist = "",
            setName = "",
            collectorNumber = "",
            stats = ""
        )
        assertFalse(
            CardPersistenceService.hasMeaningfulCardData(
                sparse.copy(title = "Title", rarity = "RARE")
            )
        )
        assertTrue(
            CardPersistenceService.hasMeaningfulCardData(
                sparse.copy(title = "Title", rarity = "RARE", typeLine = "TYPE")
            )
        )
    }

    @Test
    fun accidentalClearRequiresManyPopulatedFieldsToBeBlanked() {
        val before = CardData(
            title = "Title",
            cost = "3",
            typeLine = "TYPE",
            rarity = "RARE",
            stats = "2/2",
            artist = "Artist",
            setName = "Set",
            collectorNumber = "001/100",
            description = "Description",
            flavorText = "Flavor"
        )
        val after = before.copy(
            title = "",
            cost = "",
            typeLine = "",
            rarity = "",
            stats = "",
            artist = ""
        )

        assertTrue(CardPersistenceService.wouldClearCardAccidentally(before, after))
    }

    @Test
    fun normalEditsDoNotTriggerAccidentalClearProtection() {
        val before = CardData(
            title = "Title",
            cost = "3",
            typeLine = "TYPE",
            rarity = "RARE",
            stats = "2/2",
            artist = "Artist"
        )
        val after = before.copy(title = "Updated title", cost = "4")

        assertFalse(CardPersistenceService.wouldClearCardAccidentally(before, after))
    }

    @Test
    fun sparseCardsDoNotTriggerAccidentalClearProtection() {
        val before = CardData(title = "Only field")
        val after = CardData()

        assertFalse(CardPersistenceService.wouldClearCardAccidentally(before, after))
    }
}
