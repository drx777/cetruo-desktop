package de.cetruo.desktop

import javafx.scene.control.ComboBox

class CollectionMutationController(
    private val database: () -> CollectionDatabase?,
    private val actions: () -> CollectionEditorActions?,
    private val currentData: () -> CardData,
    private val hasSelection: () -> Boolean,
    private val saveCurrent: () -> Boolean,
    private val currentTemplate: () -> CardTemplate?,
    private val selectedTemplate: () -> CardTemplate?,
    private val templateOverrideSelected: () -> Boolean,
    private val collectionTemplateChoice: ComboBox<CardTemplate>,
    private val setCollectionDefaultTemplateName: (String) -> Unit,
    private val clearCardPreviews: () -> Unit,
    private val withSuppressedUpdates: (() -> Unit) -> Unit,
    private val reloadCurrentTemplate: () -> Unit,
    private val refreshAfterMutation: () -> Unit,
    private val setStatus: (String) -> Unit
) {
    fun setCollectionDefaultTemplate(template: CardTemplate) {
        val db = database() ?: return
        if (hasSelection() && !saveCurrent()) return

        setCollectionDefaultTemplateName(template.name)
        db.setDefaultTemplateName(template.name)
        clearCardPreviews()
        withSuppressedUpdates {
            collectionTemplateChoice.value = template
        }

        if (!templateOverrideSelected() && hasSelection()) {
            reloadCurrentTemplate()
        }
        setStatus("Collection layout: ${template.name} · cards without overrides will follow it")
    }

    fun applyCollectionDefaultTemplateToAll() {
        val selected = if (templateOverrideSelected()) {
            selectedTemplate() ?: currentTemplate()
        } else {
            currentTemplate() ?: selectedTemplate()
        } ?: run {
            setStatus("Choose a card template first.")
            return
        }
        if (hasSelection() && !saveCurrent()) return

        val result = actions()?.applyTemplateToAll(selected.name) ?: return
        setCollectionDefaultTemplateName(selected.name)
        withSuppressedUpdates {
            collectionTemplateChoice.value = selected
        }

        refreshAfterMutation()
        setStatus(
            "Applied template '${selected.name}' to ${result.changedCards} card(s); it is also the collection default."
        )
    }

    fun applyCurrentSetNameToAll() {
        val setName = currentData().setName.trim()
        if (setName.isBlank()) {
            setStatus("Enter a set name on the selected card first.")
            return
        }
        if (hasSelection() && !saveCurrent()) return

        val result = actions()?.applySetNameToAll(setName) ?: return
        refreshAfterMutation()
        setStatus("Set name applied to ${result.changedCards} card(s).")
    }

    fun normalizeCollectorTotals() {
        if (hasSelection() && !saveCurrent()) return

        val result = actions()?.normalizeCollectorTotals() ?: return
        refreshAfterMutation()
        setStatus("Collector-number totals fixed on ${result.changedCards} card(s).")
    }
}
