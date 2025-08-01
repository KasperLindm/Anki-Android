/***************************************************************************************
 *                                                                                      *
 * Copyright (c) 2012 Norbert Nagold <norbert.nagold@gmail.com>                         *
 * Copyright (c) 2014 Timothy Rae <perceptualchaos2@gmail.com>                          *
 *                                                                                      *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/

package com.ichi2.anki

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Activity.RESULT_CANCELED
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.ActionMode
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.View.OnFocusChangeListener
import android.view.ViewGroup.MarginLayoutParams
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.AdapterView.OnItemSelectedListener
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.activity.addCallback
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.CheckResult
import androidx.annotation.DrawableRes
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.FileProvider
import androidx.core.content.IntentCompat
import androidx.core.content.edit
import androidx.core.content.res.ResourcesCompat
import androidx.core.net.toUri
import androidx.core.os.BundleCompat
import androidx.core.text.HtmlCompat
import androidx.core.util.component1
import androidx.core.util.component2
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.core.view.OnReceiveContentListener
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isVisible
import androidx.draganddrop.DropHelper
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import anki.config.ConfigKey
import anki.notes.NoteFieldsCheckResponse
import com.google.android.material.color.MaterialColors
import com.google.android.material.snackbar.Snackbar
import com.ichi2.anim.ActivityTransitionAnimation
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.NoteEditorFragment.Companion.NoteEditorCaller.Companion.fromValue
import com.ichi2.anki.OnContextAndLongClickListener.Companion.setOnContextAndLongClickListener
import com.ichi2.anki.android.input.ShortcutGroup
import com.ichi2.anki.android.input.ShortcutGroupProvider
import com.ichi2.anki.android.input.shortcut
import com.ichi2.anki.bottomsheet.ImageOcclusionBottomSheetFragment
import com.ichi2.anki.common.annotations.NeedsTest
import com.ichi2.anki.common.utils.annotation.KotlinCleanup
import com.ichi2.anki.dialogs.ConfirmationDialog
import com.ichi2.anki.dialogs.DeckSelectionDialog.DeckSelectionListener
import com.ichi2.anki.dialogs.DeckSelectionDialog.SelectableDeck
import com.ichi2.anki.dialogs.DiscardChangesDialog
import com.ichi2.anki.dialogs.IntegerDialog
import com.ichi2.anki.dialogs.tags.TagsDialog
import com.ichi2.anki.dialogs.tags.TagsDialogFactory
import com.ichi2.anki.dialogs.tags.TagsDialogListener
import com.ichi2.anki.libanki.Card
import com.ichi2.anki.libanki.Collection
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.libanki.Decks.Companion.CURRENT_DECK
import com.ichi2.anki.libanki.Field
import com.ichi2.anki.libanki.Fields
import com.ichi2.anki.libanki.Note
import com.ichi2.anki.libanki.Note.ClozeUtils
import com.ichi2.anki.libanki.NoteTypeId
import com.ichi2.anki.libanki.NotetypeJson
import com.ichi2.anki.libanki.Notetypes
import com.ichi2.anki.libanki.Notetypes.Companion.NOT_FOUND_NOTE_TYPE
import com.ichi2.anki.libanki.Utils
import com.ichi2.anki.model.CardStateFilter
import com.ichi2.anki.multimedia.AudioRecordingFragment
import com.ichi2.anki.multimedia.AudioVideoFragment
import com.ichi2.anki.multimedia.MultimediaActivity.Companion.MULTIMEDIA_RESULT
import com.ichi2.anki.multimedia.MultimediaActivity.Companion.MULTIMEDIA_RESULT_FIELD_INDEX
import com.ichi2.anki.multimedia.MultimediaActivityExtra
import com.ichi2.anki.multimedia.MultimediaBottomSheet
import com.ichi2.anki.multimedia.MultimediaImageFragment
import com.ichi2.anki.multimedia.MultimediaUtils.createImageFile
import com.ichi2.anki.multimedia.MultimediaViewModel
import com.ichi2.anki.multimediacard.IMultimediaEditableNote
import com.ichi2.anki.multimediacard.fields.AudioRecordingField
import com.ichi2.anki.multimediacard.fields.EFieldType
import com.ichi2.anki.multimediacard.fields.IField
import com.ichi2.anki.multimediacard.fields.ImageField
import com.ichi2.anki.multimediacard.fields.MediaClipField
import com.ichi2.anki.multimediacard.impl.MultimediaEditableNote
import com.ichi2.anki.noteeditor.CustomToolbarButton
import com.ichi2.anki.noteeditor.FieldState
import com.ichi2.anki.noteeditor.FieldState.FieldChangeType
import com.ichi2.anki.noteeditor.NoteEditorLauncher
import com.ichi2.anki.noteeditor.Toolbar
import com.ichi2.anki.noteeditor.Toolbar.TextFormatListener
import com.ichi2.anki.noteeditor.Toolbar.TextWrapper
import com.ichi2.anki.observability.undoableOp
import com.ichi2.anki.pages.ImageOcclusion
import com.ichi2.anki.preferences.sharedPrefs
import com.ichi2.anki.previewer.TemplatePreviewerArguments
import com.ichi2.anki.previewer.TemplatePreviewerPage
import com.ichi2.anki.servicelayer.LanguageHintService.languageHint
import com.ichi2.anki.servicelayer.NoteService
import com.ichi2.anki.snackbar.BaseSnackbarBuilderProvider
import com.ichi2.anki.snackbar.SnackbarBuilder
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.anki.ui.setupNoteTypeSpinner
import com.ichi2.anki.utils.ext.sharedPrefs
import com.ichi2.anki.utils.ext.showDialogFragment
import com.ichi2.anki.utils.ext.window
import com.ichi2.compat.CompatHelper.Companion.getSerializableCompat
import com.ichi2.compat.setTooltipTextCompat
import com.ichi2.imagecropper.ImageCropper
import com.ichi2.imagecropper.ImageCropper.Companion.CROP_IMAGE_RESULT
import com.ichi2.imagecropper.ImageCropperLauncher
import com.ichi2.themes.Themes
import com.ichi2.utils.AndroidUiUtils.showSoftInput
import com.ichi2.utils.ClipboardUtil
import com.ichi2.utils.ClipboardUtil.MEDIA_MIME_TYPES
import com.ichi2.utils.ClipboardUtil.hasMedia
import com.ichi2.utils.ClipboardUtil.items
import com.ichi2.utils.ContentResolverUtil
import com.ichi2.utils.HashUtil
import com.ichi2.utils.ImportUtils
import com.ichi2.utils.IntentUtil.resolveMimeType
import com.ichi2.utils.KeyUtils
import com.ichi2.utils.MapUtil
import com.ichi2.utils.NoteFieldDecorator
import com.ichi2.utils.TextViewUtil
import com.ichi2.utils.configureView
import com.ichi2.utils.message
import com.ichi2.utils.negativeButton
import com.ichi2.utils.neutralButton
import com.ichi2.utils.positiveButton
import com.ichi2.utils.show
import com.ichi2.utils.title
import com.ichi2.widget.WidgetStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONArray
import timber.log.Timber
import java.io.File
import java.util.LinkedList
import java.util.Locale
import java.util.function.Consumer
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

const val CALLER_KEY = "caller"

/**
 * Allows the user to edit a note, for instance if there is a typo. A card is a presentation of a note, and has two
 * sides: a question and an answer. Any number of fields can appear on each side. When you add a note to Anki, cards
 * which show that note are generated. Some models generate one card, others generate more than one.
 * Features:
 * - Implements [MenuHost] ([onCreateMenu]/[onPrepareMenu]) to handle toolbar menu item clicks.
 * - Implements [DispatchKeyEventListener] to handle key events.
 *
 * @see [the Anki Desktop manual](https://docs.ankiweb.net/getting-started.html.cards)
 */
@KotlinCleanup("Go through the class and select elements to fix")
@KotlinCleanup("see if we can lateinit")
class NoteEditorFragment :
    Fragment(R.layout.note_editor_fragment),
    DeckSelectionListener,
    TagsDialogListener,
    BaseSnackbarBuilderProvider,
    DispatchKeyEventListener,
    MenuProvider,
    ShortcutGroupProvider {
    /** Whether any change are saved. E.g. multimedia, new card added, field changed and saved. */
    private var changed = false
    private var isTagsEdited = false
    private var isFieldEdited = false

    private var multimediaActionJob: Job? = null

    private val getColUnsafe: Collection
        get() = CollectionManager.getColUnsafe()

    /**
     * Flag which forces the calling activity to rebuild it's definition of current card from scratch
     */
    private var reloadRequired = false

    private var fieldsLayoutContainer: LinearLayout? = null
    private var tagsDialogFactory: TagsDialogFactory? = null
    private var tagsButton: AppCompatButton? = null
    private var cardsButton: AppCompatButton? = null

    @VisibleForTesting
    internal var noteTypeSpinner: Spinner? = null
    private var deckSpinnerSelection: DeckSpinnerSelection? = null
    private var imageOcclusionButtonsContainer: LinearLayout? = null
    private var selectImageForOcclusionButton: Button? = null
    private var editOcclusionsButton: Button? = null
    private var pasteOcclusionImageButton: Button? = null

    // non-null after onCollectionLoaded
    private var editorNote: Note? = null

    private val multimediaViewModel: MultimediaViewModel by activityViewModels()

    private var currentImageOccPath: String? = null

    // Null if adding a new card. Presently NonNull if editing an existing note - but this is subject to change
    private var currentEditedCard: Card? = null
    private var selectedTags: MutableList<String>? = null

    @get:VisibleForTesting
    var deckId: DeckId = 0
        private set
    private var allNoteTypeIds: List<Long>? = null

    @KotlinCleanup("this ideally should be Int, Int?")
    private var noteTypeChangeFieldMap: MutableMap<Int, Int>? = null
    private var noteTypeChangeCardMap: HashMap<Int, Int?>? = null
    private val customViewIds = ArrayList<Int>()

    // indicates if a new note is added or a card is edited
    private var addNote = false
    private var aedictIntent = false

    // indicates which activity called Note Editor
    private var caller = NoteEditorCaller.NO_CALLER
    private var editFields: LinkedList<FieldEditText>? = null
    private var sourceText: Array<String?>? = null
    private val fieldState = FieldState.fromEditor(this)
    private lateinit var toolbar: Toolbar

    // Use the same HTML if the same image is pasted multiple times.
    private var pastedImageCache: HashMap<String, String> = HashMap()

    // save field index as key and text as value when toggle sticky clicked in Field Edit Text
    private var toggleStickyText: HashMap<Int, String?> = HashMap()

    var clipboard: ClipboardManager? = null

    /**
     * Whether this is displayed in a fragment view.
     * If true, this fragment is on the trailing side of the card browser.
     */
    private val inFragmentedActivity
        get() = requireArguments().getBoolean(IN_FRAGMENTED_ACTIVITY)

    private val requestAddLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
            NoteEditorActivityResultCallback {
                if (it.resultCode != RESULT_CANCELED) {
                    changed = true
                }
            },
        )

    private val multimediaFragmentLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
            NoteEditorActivityResultCallback { result ->
                if (result.resultCode == RESULT_CANCELED) {
                    Timber.d("Multimedia result canceled")
                    val index = result.data?.extras?.getInt(MULTIMEDIA_RESULT_FIELD_INDEX) ?: return@NoteEditorActivityResultCallback
                    showMultimediaBottomSheet()
                    handleMultimediaActions(index)
                    return@NoteEditorActivityResultCallback
                }

                Timber.d("Getting multimedia result")
                val extras = result.data?.extras ?: return@NoteEditorActivityResultCallback
                handleMultimediaResult(extras)
            },
        )

    private val requestTemplateEditLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
            NoteEditorActivityResultCallback {
                // Note type can change regardless of exit type - update ourselves and CardBrowser
                reloadRequired = true
                editorNote!!.notetype = getColUnsafe.notetypes.get(editorNote!!.noteTypeId)!!
                if (currentEditedCard == null ||
                    !editorNote!!
                        .cardIds(getColUnsafe)
                        .contains(currentEditedCard!!.id)
                ) {
                    if (!addNote) {
                    /* This can occur, for example, if the
                     * card type was deleted or if the note
                     * type was changed without moving this
                     * card to another type. */
                        Timber.d("onActivityResult() template edit return - current card is gone, close note editor")
                        showSnackbar(getString(R.string.template_for_current_card_deleted))
                        closeNoteEditor()
                    } else {
                        Timber.d("onActivityResult() template edit return, in add mode, just re-display")
                    }
                } else {
                    Timber.d("onActivityResult() template edit return - current card exists")
                    // reload current card - the template ordinals are possibly different post-edit
                    currentEditedCard = getColUnsafe.getCard(currentEditedCard!!.id)
                    @NeedsTest("#17282 returning from template editor saves further made changes")
                    // make sure the card's note is available going forward
                    currentEditedCard!!.note(getColUnsafe)
                    editorNote = currentEditedCard!!.note // update the NoteEditor's working note reference
                    updateCards(editorNote!!.notetype)
                }
            },
        )

    private val ioEditorLauncher =
        registerForActivityResult(
            ActivityResultContracts.GetContent(),
        ) { uri ->
            if (uri != null) {
                ImportUtils.getFileCachedCopy(requireContext(), uri)?.let { path ->
                    setupImageOcclusionEditor(path)
                }
            }
        }

    private val requestIOEditorCloser =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
            NoteEditorActivityResultCallback { result ->
                if (result.resultCode != RESULT_CANCELED) {
                    changed = true
                    if (!addNote) {
                        reloadRequired = true
                        closeNoteEditor(RESULT_UPDATED_IO_NOTE, null)
                    }
                }
            },
        )

    /**
     * Listener for handling content received via drag and drop or copy and paste.
     * This listener processes URIs contained in the payload and attempts to paste the content into the target EditText view.
     */
    private val onReceiveContentListener =
        OnReceiveContentListener { view, payload ->
            val (uriContent, remaining) = payload.partition { item -> item.uri != null }

            if (uriContent == null) {
                return@OnReceiveContentListener remaining
            }

            val clip = uriContent.clip
            val description = clip.description

            if (!hasMedia(description)) {
                return@OnReceiveContentListener remaining
            }

            for (uri in clip.items().map { it.uri }) {
                lifecycleScope.launch {
                    try {
                        val pasteAsPng = shouldPasteAsPng()
                        onPaste(view as EditText, uri, description, pasteAsPng)
                    } catch (e: Exception) {
                        Timber.w(e)
                        CrashReportService.sendExceptionReport(e, "NoteEditor::onReceiveContent")
                    }
                }
            }

            return@OnReceiveContentListener remaining
        }

    private inner class NoteEditorActivityResultCallback(
        private val callback: (result: ActivityResult) -> Unit,
    ) : ActivityResultCallback<ActivityResult> {
        override fun onActivityResult(result: ActivityResult) {
            Timber.d("onActivityResult() with result: %s", result.resultCode)
            if (result.resultCode == DeckPicker.RESULT_DB_ERROR) {
                closeNoteEditor(DeckPicker.RESULT_DB_ERROR, null)
            }
            callback(result)
        }
    }

    override fun onDeckSelected(deck: SelectableDeck?) {
        if (deck == null) {
            return
        }
        deckId = deck.deckId
        // this is called because DeckSpinnerSelection.onDeckAdded doesn't update the list
        deckSpinnerSelection!!.initializeNoteEditorDeckSpinner(getColUnsafe)
        launchCatchingTask {
            deckSpinnerSelection!!.selectDeckById(deckId, false)
        }
    }

    private enum class AddClozeType {
        SAME_NUMBER,
        INCREMENT_NUMBER,
    }

    @VisibleForTesting
    var addNoteErrorMessage: String? = null

    private fun displayErrorSavingNote() {
        val errorMessage = snackbarErrorText
        // Anki allows to proceed in case we try to add non cloze text in cloze field with warning,
        // this snackbar helps replicate similar behaviour
        if (errorMessage == TR.addingYouHaveAClozeDeletionNote()) {
            noClozeDialog(errorMessage)
        } else {
            showSnackbar(errorMessage)
        }
    }

    private fun noClozeDialog(errorMessage: String) {
        AlertDialog.Builder(requireContext()).show {
            message(text = errorMessage)
            positiveButton(text = TR.actionsSave()) {
                lifecycleScope.launch {
                    saveNoteWithProgress()
                }
            }
            negativeButton(R.string.dialog_cancel)
        }
    }

    @VisibleForTesting
    val snackbarErrorText: String
        get() =
            when {
                addNoteErrorMessage != null -> addNoteErrorMessage!!
                allFieldsHaveContent() -> resources.getString(R.string.note_editor_no_cards_created_all_fields)
                else -> resources.getString(R.string.note_editor_no_cards_created)
            }

    override val baseSnackbarBuilder: SnackbarBuilder = {
        if (sharedPrefs().getBoolean(PREF_NOTE_EDITOR_SHOW_TOOLBAR, true)) {
            anchorView = requireView().findViewById<Toolbar>(R.id.editor_toolbar)
        }
    }

    private fun allFieldsHaveContent() = currentFieldStrings.none { it.isNullOrEmpty() }

    // ----------------------------------------------------------------------------
    // ANDROID METHODS
    // ----------------------------------------------------------------------------
    override fun onCreate(savedInstanceState: Bundle?) {
        tagsDialogFactory = TagsDialogFactory(this).attachToFragmentManager<TagsDialogFactory>(parentFragmentManager)
        super.onCreate(savedInstanceState)
        fieldState.setInstanceState(savedInstanceState)
        val intent = requireActivity().intent
        if (savedInstanceState != null) {
            caller = fromValue(savedInstanceState.getInt(CALLER_KEY))
            addNote = savedInstanceState.getBoolean("addNote")
            deckId = savedInstanceState.getLong("did")
            selectedTags = savedInstanceState.getStringArrayList("tags")
            reloadRequired = savedInstanceState.getBoolean(RELOAD_REQUIRED_EXTRA_KEY)
            pastedImageCache =
                savedInstanceState.getSerializableCompat<HashMap<String, String>>("imageCache")!!
            toggleStickyText =
                savedInstanceState.getSerializableCompat<HashMap<Int, String?>>("toggleSticky")!!
            changed = savedInstanceState.getBoolean(NOTE_CHANGED_EXTRA_KEY)
        } else {
            caller = fromValue(requireArguments().getInt(EXTRA_CALLER, NoteEditorCaller.NO_CALLER.value))
            if (caller == NoteEditorCaller.NO_CALLER) {
                val action = intent.action
                if (ACTION_CREATE_FLASHCARD == action || ACTION_CREATE_FLASHCARD_SEND == action || Intent.ACTION_PROCESS_TEXT == action) {
                    caller = NoteEditorCaller.NOTEEDITOR_INTENT_ADD
                }
            }
        }
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = false
        @Suppress("deprecation", "API35 properly handle edge-to-edge")
        requireActivity().window.statusBarColor = Themes.getColorFromAttr(requireContext(), R.attr.appBarColor)
        super.onViewCreated(view, savedInstanceState)
        // Set up toolbar
        toolbar = view.findViewById(R.id.editor_toolbar)
        toolbar.apply {
            formatListener =
                TextFormatListener { formatter: Toolbar.TextFormatter ->
                    val currentFocus = requireActivity().currentFocus as? FieldEditText ?: return@TextFormatListener
                    modifyCurrentSelection(formatter, currentFocus)
                }
            // Sets the background and icon color of toolbar respectively.
            setBackgroundColor(
                MaterialColors.getColor(
                    requireContext(),
                    R.attr.toolbarBackgroundColor,
                    0,
                ),
            )
            setIconColor(MaterialColors.getColor(requireContext(), R.attr.toolbarIconColor, 0))
        }

        try {
            setupEditor(getColUnsafe)
        } catch (ex: RuntimeException) {
            Timber.w(ex, "setupEditor")
            requireAnkiActivity().onCollectionLoadError()
            return
        }
        // TODO this callback doesn't handle predictive back navigation!
        // see #14678, added to temporarily fix for a bug
        requireActivity().onBackPressedDispatcher.addCallback(this) {
            Timber.i("NoteEditor:: onBackPressed()")
            closeCardEditorWithCheck()
        }

        @Suppress("deprecation", "API35 properly handle edge-to-edge")
        requireActivity().window.navigationBarColor =
            Themes.getColorFromAttr(requireContext(), R.attr.toolbarBackgroundColor)

        // Register this fragment as a menu provider with the activity
        (requireActivity() as MenuHost).addMenuProvider(
            this,
            viewLifecycleOwner,
            Lifecycle.State.RESUMED,
        )
    }

    /**
     * Handles an intent containing an image from the user's gallery or the internet by opening
     * MultimediaActivity specifically for creating a new card.
     *
     * It extracts the image URI from the intent data based on the intent's action.
     * If the action is `Intent.ACTION_SEND`, the method uses `IntentCompat.getParcelableExtra` to retrieve
     * the image URI from the `Intent.EXTRA_STREAM` extra. Otherwise, it assumes the image URI is directly
     * available in the intent's data field.
     *
     * @param data the Intent containing the image information from the user's share action
     */
    @NeedsTest("Test when the user directly passes image to the edit note field")
    private suspend fun handleImageIntent(data: Intent) {
        val imageUri =
            if (data.action == Intent.ACTION_SEND) {
                BundleCompat.getParcelable(requireArguments(), Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                data.data
            }

        if (imageUri == null) {
            Timber.d("NoteEditor:: Image Uri is null")
            showSnackbar(R.string.something_wrong)
            return
        }

        try {
            requireContext().contentResolver.takePersistableUriPermission(imageUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            Timber.d("Persisted URI permission for $imageUri")
        } catch (e: SecurityException) {
            Timber.w(e, "Unable to persist URI permission")
        }

        val cachedImagePath = copyUriToInternalCache(imageUri)
        if (cachedImagePath == null) {
            Timber.w("Failed to cache image")
            showSnackbar(R.string.something_wrong)
            return
        }
        val cachedUri = Uri.fromFile(File(requireContext().cacheDir, cachedImagePath))

        val note = getCurrentMultimediaEditableNote()
        if (note.isEmpty) {
            Timber.w("Note is null, returning")
            return
        }
        openMultimediaImageFragment(
            fieldIndex = 0,
            field = ImageField(),
            multimediaNote = note,
            imageUri = cachedUri,
        )
    }

    /**
     * Copies a given [Uri] to the app's internal cache directory.
     *
     * This is necessary because URIs provided by other apps (e.g., WhatsApp, gallery apps) via
     * `Intent` are usually content URIs with temporary permissions that are only valid
     * in the originating context (like an Activity). Once passed to other components (like Fragments),
     * these permissions may be lost, resulting in a SecurityException.
     *
     * By caching the file in internal storage and referencing it via a file URI,
     * we ensure persistent access to the image without relying on external content providers.
     *
     * @param uri The [Uri] pointing to the external image content.
     * @return The name of the cached file, or `null` if the operation failed.
     */
    private fun copyUriToInternalCache(uri: Uri): String? {
        return try {
            val inputStream = requireContext().contentResolver.openInputStream(uri) ?: return null

            val fileName = ContentResolverUtil.getFileName(requireContext().contentResolver, uri)
            val cacheDir = requireContext().cacheDir
            val destFile = File(cacheDir, fileName)

            val canonicalCacheDir = cacheDir.canonicalFile
            val canonicalDestFile = destFile.canonicalFile

            if (!canonicalDestFile.path.startsWith(canonicalCacheDir.path)) {
                Timber.w("Rejected path due to directory traversal risk: $fileName")
                return null
            }

            destFile.outputStream().use { output ->
                inputStream.copyTo(output)
            }

            Timber.d("copyUriToInternalCache() copied to ${destFile.absolutePath}")
            destFile.name
        } catch (e: Exception) {
            Timber.w(e, "Failed to copy URI to internal cache")
            null
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        addInstanceStateToBundle(outState)
        super.onSaveInstanceState(outState)
    }

    private fun addInstanceStateToBundle(savedInstanceState: Bundle) {
        Timber.i("Saving instance")
        savedInstanceState.putInt(CALLER_KEY, caller.value)
        savedInstanceState.putBoolean("addNote", addNote)
        savedInstanceState.putLong("did", deckId)
        savedInstanceState.putBoolean(NOTE_CHANGED_EXTRA_KEY, changed)
        savedInstanceState.putBoolean(RELOAD_REQUIRED_EXTRA_KEY, reloadRequired)
        savedInstanceState.putIntegerArrayList("customViewIds", customViewIds)
        savedInstanceState.putSerializable("imageCache", pastedImageCache)
        savedInstanceState.putSerializable("toggleSticky", toggleStickyText)
        if (selectedTags == null) {
            selectedTags = ArrayList(0)
        }
        savedInstanceState.putStringArrayList("tags", selectedTags?.let { ArrayList(it) })
    }

    // Finish initializing the fragment after the collection has been correctly loaded
    private fun setupEditor(col: Collection) {
        val intent = requireActivity().intent
        Timber.d("NoteEditor() onCollectionLoaded: caller: %s", caller)
        requireAnkiActivity().registerReceiver()
        fieldsLayoutContainer = requireView().findViewById(R.id.CardEditorEditFieldsLayout)
        tagsButton = requireView().findViewById(R.id.CardEditorTagButton)
        cardsButton = requireView().findViewById(R.id.CardEditorCardsButton)
        cardsButton!!.setOnClickListener {
            Timber.i("NoteEditor:: Cards button pressed. Opening template editor")
            showCardTemplateEditor()
        }
        imageOcclusionButtonsContainer = requireView().findViewById(R.id.ImageOcclusionButtonsLayout)
        editOcclusionsButton = requireView().findViewById(R.id.EditOcclusionsButton)
        selectImageForOcclusionButton = requireView().findViewById(R.id.SelectImageForOcclusionButton)
        pasteOcclusionImageButton = requireView().findViewById(R.id.PasteImageForOcclusionButton)

        try {
            clipboard = requireActivity().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        } catch (e: Exception) {
            Timber.w(e)
        }

        aedictIntent = false
        currentEditedCard = null
        when (caller) {
            NoteEditorCaller.NO_CALLER -> {
                Timber.e("no caller could be identified, closing")
                requireActivity().finish()
                return
            }
            NoteEditorCaller.EDIT -> {
                val cardId = requireNotNull(requireArguments().getLong(EXTRA_CARD_ID)) { "EXTRA_CARD_ID" }
                currentEditedCard = col.getCard(cardId)
                editorNote = currentEditedCard!!.note(col)
                addNote = false
            }
            NoteEditorCaller.PREVIEWER_EDIT -> {
                val id = requireArguments().getLong(EXTRA_EDIT_FROM_CARD_ID)
                currentEditedCard = col.getCard(id)
                editorNote = currentEditedCard!!.note(getColUnsafe)
            }
            NoteEditorCaller.STUDYOPTIONS,
            NoteEditorCaller.DECKPICKER,
            NoteEditorCaller.REVIEWER_ADD,
            NoteEditorCaller.CARDBROWSER_ADD,
            NoteEditorCaller.NOTEEDITOR,
            -> {
                addNote = true
            }
            NoteEditorCaller.NOTEEDITOR_INTENT_ADD,
            NoteEditorCaller.INSTANT_NOTE_EDITOR,
            -> {
                fetchIntentInformation(intent)
                if (sourceText == null) {
                    requireActivity().finish()
                    return
                }
                if ("Aedict Notepad" == sourceText!![0] && addFromAedict(sourceText!![1])) {
                    requireActivity().finish()
                    return
                }
                addNote = true
            }
            // image occlusion is handled at the end of this method, grep: CALLER_IMG_OCCLUSION
            // we need to have loaded the current note type
            NoteEditorCaller.IMG_OCCLUSION, NoteEditorCaller.ADD_IMAGE -> {
                addNote = true
            }
        }

        if (addNote) {
            editOcclusionsButton?.visibility = View.GONE
            selectImageForOcclusionButton?.setOnClickListener {
                Timber.i("selecting image for occlusion")
                val imageOcclusionBottomSheet = ImageOcclusionBottomSheetFragment()
                imageOcclusionBottomSheet.listener =
                    object : ImageOcclusionBottomSheetFragment.ImagePickerListener {
                        override fun onCameraClicked() {
                            Timber.i("onCameraClicked")
                            dispatchCameraEvent()
                        }

                        override fun onGalleryClicked() {
                            Timber.i("onGalleryClicked")
                            try {
                                ioEditorLauncher.launch("image/*")
                            } catch (_: ActivityNotFoundException) {
                                Timber.w("No app found to handle onGalleryClicked request")
                                activity?.showSnackbar(R.string.activity_start_failed)
                            }
                        }
                    }
                imageOcclusionBottomSheet.show(
                    parentFragmentManager,
                    "ImageOcclusionBottomSheetFragment",
                )
            }

            pasteOcclusionImageButton?.text = TR.notetypesIoPasteImageFromClipboard()
            pasteOcclusionImageButton?.setOnClickListener {
                // TODO: Support all extensions
                //  See https://github.com/ankitects/anki/blob/6f3550464d37aee1b8b784e431cbfce8382d3ce7/rslib/src/image_occlusion/imagedata.rs#L154
                if (ClipboardUtil.hasImage(clipboard)) {
                    val uri = ClipboardUtil.getUri(clipboard)
                    val i =
                        Intent().apply {
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            clipData = ClipData.newUri(requireActivity().contentResolver, uri.toString(), uri)
                        }
                    ImportUtils.getFileCachedCopy(requireContext(), i)?.let { path ->
                        setupImageOcclusionEditor(path)
                    }
                } else {
                    showSnackbar(TR.editingNoImageFoundOnClipboard())
                }
            }
        } else {
            selectImageForOcclusionButton?.visibility = View.GONE
            pasteOcclusionImageButton?.visibility = View.GONE
            editOcclusionsButton?.visibility = View.VISIBLE
            editOcclusionsButton?.text = resources.getString(R.string.edit_occlusions)
            editOcclusionsButton?.setOnClickListener {
                setupImageOcclusionEditor()
            }
        }

        // Note type Selector
        noteTypeSpinner = requireView().findViewById(R.id.note_type_spinner)
        allNoteTypeIds = setupNoteTypeSpinner(requireContext(), noteTypeSpinner!!, col)

        // Deck Selector
        val deckTextView = requireView().findViewById<TextView>(R.id.CardEditorDeckText)
        // If edit mode and more than one card template distinguish between "Deck" and "Card deck"
        if (!addNote && editorNote!!.notetype.templates.length() > 1) {
            deckTextView.setText(R.string.CardEditorCardDeck)
        }
        deckSpinnerSelection =
            DeckSpinnerSelection(
                requireContext() as AppCompatActivity,
                requireView().findViewById(R.id.note_deck_spinner),
                showAllDecks = false,
                alwaysShowDefault = true,
                showFilteredDecks = false,
                fragmentManagerSupplier = { childFragmentManager },
            )
        deckSpinnerSelection!!.initializeNoteEditorDeckSpinner(col)
        deckId = requireArguments().getLong(EXTRA_DID, deckId)
        val getTextFromSearchView = requireArguments().getString(EXTRA_TEXT_FROM_SEARCH_VIEW)
        setDid(editorNote)
        setNote(editorNote, FieldChangeType.onActivityCreation(shouldReplaceNewlines()))
        if (addNote) {
            noteTypeSpinner!!.onItemSelectedListener = SetNoteTypeListener()
            requireAnkiActivity().setToolbarTitle(R.string.menu_add)
            // set information transferred by intent
            var contents: String? = null
            val tags = requireArguments().getStringArray(EXTRA_TAGS)

            try {
                // If content has been shared, we can't share to an image occlusion note type
                if (currentNotetypeIsImageOcclusion() && (sourceText != null || caller == NoteEditorCaller.ADD_IMAGE)) {
                    val noteType =
                        col.notetypes.all().first {
                            !it.isImageOcclusion
                        }
                    changeNoteType(noteType.id)
                }
            } catch (e: NoSuchElementException) {
                showSnackbar(R.string.missing_note_type)
                // setting the text to null & caller to CALLER_NO_CALLER would skip adding text/image to edit field
                sourceText = null
                caller = NoteEditorCaller.NO_CALLER
                Timber.w(e)
            }

            if (sourceText != null) {
                if (aedictIntent && editFields!!.size == 3 && sourceText!![1]!!.contains("[")) {
                    contents =
                        sourceText!![1]!!
                            .replaceFirst("\\[".toRegex(), "\u001f" + sourceText!![0] + "\u001f")
                    contents = contents.substring(0, contents.length - 1)
                } else if (!editFields!!.isEmpty()) {
                    editFields!![0].setText(sourceText!![0])
                    if (editFields!!.size > 1) {
                        editFields!![1].setText(sourceText!![1])
                    }
                }
            } else {
                contents = requireArguments().getString(EXTRA_CONTENTS)
            }
            contents?.let { setEditFieldTexts(it) }
            tags?.let { setTags(it) }
            // If the activity was called to handle an image addition, launch a coroutine to process the image intent.
            if (caller == NoteEditorCaller.ADD_IMAGE) lifecycleScope.launch { handleImageIntent(intent) }
        } else {
            noteTypeSpinner!!.onItemSelectedListener = EditNoteTypeListener()
            requireAnkiActivity().setTitle(R.string.cardeditor_title_edit_card)
        }
        requireView().findViewById<View>(R.id.CardEditorTagButton).setOnClickListener {
            Timber.i("NoteEditor:: Tags button pressed... opening tags editor")
            showTagsDialog()
        }
        if (!addNote && currentEditedCard != null) {
            Timber.i(
                "onCollectionLoaded() Edit note activity successfully started with card id %d",
                currentEditedCard!!.id,
            )
        }
        if (addNote) {
            Timber.i(
                "onCollectionLoaded() Edit note activity successfully started in add card mode with node id %d",
                editorNote!!.id,
            )
        }

        // don't open keyboard if not adding note
        if (!addNote) {
            requireActivity().window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        }

        // set focus to FieldEditText 'first' on startup like Anki desktop
        if (editFields != null && !editFields!!.isEmpty()) {
            // EXTRA_TEXT_FROM_SEARCH_VIEW takes priority over other intent inputs
            if (!getTextFromSearchView.isNullOrEmpty()) {
                editFields!!.first().setText(getTextFromSearchView)
            }
            editFields!!.first().requestFocus()
        }

        if (caller == NoteEditorCaller.IMG_OCCLUSION) {
            // val saveImageUri = ImageIntentManager.getImageUri()
            val saveImageUri = BundleCompat.getParcelable(requireArguments(), EXTRA_IMG_OCCLUSION, Uri::class.java)
            if (saveImageUri != null) {
                ImportUtils.getFileCachedCopy(requireContext(), saveImageUri)?.let { path ->
                    setupImageOcclusionEditor(path)
                }
            } else {
                Timber.w("Image uri is null")
            }
        }
    }

    private val cameraLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { isPictureTaken ->
            if (isPictureTaken) {
                currentImageOccPath?.let { imagePath ->
                    val photoFile = File(imagePath)
                    val imageUri: Uri =
                        FileProvider.getUriForFile(
                            requireContext(),
                            requireActivity().packageName + ".apkgfileprovider",
                            photoFile,
                        )
                    startCrop(imageUri)
                }
            } else {
                Timber.d("Camera aborted or some interruption")
            }
        }

    /** Launches an activity to crop the image, using the [ImageCropper] */
    private val cropImageLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            when (result.resultCode) {
                Activity.RESULT_OK -> {

                    result.data?.let {
                        val cropResultData =
                            IntentCompat.getParcelableExtra(
                                it,
                                CROP_IMAGE_RESULT,
                                ImageCropper.CropResultData::class.java,
                            )
                        Timber.d("Cropped image data: $cropResultData")
                        if (cropResultData?.uriPath == null) return@registerForActivityResult
                        setupImageOcclusionEditor(cropResultData.uriPath)
                    }
                }

                else -> {
                    Timber.v("Unable to crop the image")
                }
            }
        }

    private fun startCrop(imageUri: Uri) {
        Timber.i("launching crop")
        val intent = ImageCropperLauncher.ImageUri(imageUri).getIntent(requireContext())
        cropImageLauncher.launch(intent)
    }

    private fun dispatchCameraEvent() {
        val photoFile: File? =
            try {
                requireContext().createImageFile()
            } catch (e: Exception) {
                Timber.w(e, "Error creating the file")
                return
            }

        currentImageOccPath = photoFile?.absolutePath

        photoFile?.let {
            val photoURI: Uri =
                FileProvider.getUriForFile(
                    requireContext(),
                    requireActivity().packageName + ".apkgfileprovider",
                    it,
                )
            cameraLauncher.launch(photoURI)
        }
    }

    private fun modifyCurrentSelection(
        formatter: Toolbar.TextFormatter,
        textBox: FieldEditText,
    ) {
        // get the current text and selection locations
        val selectionStart = textBox.selectionStart
        val selectionEnd = textBox.selectionEnd

        // #6762 values are reversed if using a keyboard and pressing Ctrl+Shift+LeftArrow
        val start = min(selectionStart, selectionEnd)
        val end = max(selectionStart, selectionEnd)
        val text = textBox.text?.toString() ?: ""

        // Split the text in the places where the formatting will take place
        val beforeText = text.substring(0, start)
        val selectedText = text.substring(start, end)
        val afterText = text.substring(end)
        val (newText, newStart, newEnd) = formatter.format(selectedText)

        // Update text field with updated text and selection
        val length = beforeText.length + newText.length + afterText.length
        val newFieldContent =
            StringBuilder(length).append(beforeText).append(newText).append(afterText)
        textBox.setText(newFieldContent)
        textBox.setSelection(start + newStart, start + newEnd)
    }

    override fun onStop() {
        super.onStop()
        if (!isRemoving) {
            WidgetStatus.updateInBackground(requireContext())
        }
    }

    @KotlinCleanup("convert KeyUtils to extension functions")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // We want to behave as onKeyUp and thus only react to ACTION_UP
        if (event.action != KeyEvent.ACTION_UP) return false
        val keyCode = event.keyCode
        if (toolbar.onKeyUp(keyCode, event)) {
            // Toolbar was able to handle this key event. No need to handle it in NoteEditor too.
            return true
        }
        when (keyCode) {
            KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_ENTER ->
                if (event.isCtrlPressed) {
                    // disable it in case of image occlusion
                    if (allowSaveAndPreview()) {
                        launchCatchingTask { saveNote() }
                        return true
                    }
                }
            KeyEvent.KEYCODE_D -> // null check in case Spinner is moved into options menu in the future
                if (event.isCtrlPressed) {
                    launchCatchingTask { deckSpinnerSelection!!.displayDeckSelectionDialog() }
                    return true
                }
            KeyEvent.KEYCODE_L ->
                if (event.isCtrlPressed) {
                    showCardTemplateEditor()
                    return true
                }
            KeyEvent.KEYCODE_N ->
                if (event.isCtrlPressed && noteTypeSpinner != null) {
                    noteTypeSpinner!!.performClick()
                    return true
                }
            KeyEvent.KEYCODE_T ->
                if (event.isCtrlPressed && event.isShiftPressed) {
                    showTagsDialog()
                    return true
                }
            KeyEvent.KEYCODE_C -> {
                if (event.isCtrlPressed && event.isShiftPressed) {
                    insertCloze(if (event.isAltPressed) AddClozeType.SAME_NUMBER else AddClozeType.INCREMENT_NUMBER)
                    // Anki Desktop warns, but still inserts the cloze
                    if (!isClozeType) {
                        showSnackbar(R.string.note_editor_insert_cloze_no_cloze_note_type)
                    }
                    return true
                }
            }
            KeyEvent.KEYCODE_P -> {
                if (event.isCtrlPressed) {
                    Timber.i("Ctrl+P: Preview Pressed")
                    if (allowSaveAndPreview()) {
                        launchCatchingTask { performPreview() }
                        return true
                    }
                }
            }
        }

        // 7573: Ctrl+Shift+[Num] to select a field
        if (event.isCtrlPressed && event.isShiftPressed) {
            val digit = KeyUtils.getDigit(event) ?: return false
            // '0' is after '9' on the keyboard, so a user expects '10'
            val humanReadableDigit = if (digit == 0) 10 else digit
            // Subtract 1 to map to field index. '1' is the first field (index 0)
            selectFieldIndex(humanReadableDigit - 1)
            return true
        }
        return false
    }

    private fun selectFieldIndex(index: Int) {
        Timber.i("Selecting field index %d", index)
        if (editFields!!.size <= index || index < 0) {
            Timber.i("Index out of range: %d", index)
            return
        }
        val field: FieldEditText? =
            try {
                editFields!![index]
            } catch (e: IndexOutOfBoundsException) {
                Timber.w(e, "Error selecting index %d", index)
                return
            }
        field!!.requestFocus()
        Timber.d("Selected field")
    }

    private fun insertCloze(addClozeType: AddClozeType) {
        val v = requireActivity().currentFocus as? FieldEditText ?: return
        convertSelectedTextToCloze(v, addClozeType)
    }

    private fun fetchIntentInformation(intent: Intent) {
        val extras = requireArguments()
        sourceText = arrayOfNulls(2)
        if (Intent.ACTION_PROCESS_TEXT == intent.action) {
            val stringExtra = extras.getString(Intent.EXTRA_PROCESS_TEXT)
            Timber.d("Obtained %s from intent: %s", stringExtra, Intent.EXTRA_PROCESS_TEXT)
            sourceText!![0] = stringExtra ?: ""
            sourceText!![1] = ""
        } else if (ACTION_CREATE_FLASHCARD == intent.action) {
            // mSourceLanguage = extras.getString(SOURCE_LANGUAGE);
            // mTargetLanguage = extras.getString(TARGET_LANGUAGE);
            sourceText!![0] = extras.getString(SOURCE_TEXT)
            sourceText!![1] = extras.getString(TARGET_TEXT)
        } else {
            var first: String?
            var second: String?
            first =
                if (extras.getString(Intent.EXTRA_SUBJECT) != null) {
                    extras.getString(Intent.EXTRA_SUBJECT)
                } else {
                    ""
                }
            second =
                if (extras.getString(Intent.EXTRA_TEXT) != null) {
                    extras.getString(Intent.EXTRA_TEXT)
                } else {
                    ""
                }
            // Some users add cards via SEND intent from clipboard. In this case SUBJECT is empty
            if ("" == first) {
                // Assume that if only one field was sent then it should be the front
                first = second
                second = ""
            }
            val messages = Pair(first, second)
            sourceText!![0] = messages.first
            sourceText!![1] = messages.second
        }
    }

    private fun addFromAedict(extraText: String?): Boolean {
        var category: String
        val notepadLines = extraText!!.split("\n".toRegex()).toTypedArray()
        for (i in notepadLines.indices) {
            if (notepadLines[i].startsWith("[") && notepadLines[i].endsWith("]")) {
                category = notepadLines[i].substring(1, notepadLines[i].length - 1)
                if ("default" == category) {
                    if (notepadLines.size > i + 1) {
                        val entryLines = notepadLines[i + 1].split(":".toRegex()).toTypedArray()
                        if (entryLines.size > 1) {
                            sourceText!![0] = entryLines[1]
                            sourceText!![1] = entryLines[0]
                            aedictIntent = true
                            return false
                        }
                    }
                    showSnackbar(resources.getString(R.string.intent_aedict_empty))
                    return true
                }
            }
        }
        showSnackbar(resources.getString(R.string.intent_aedict_category))
        return true
    }

    @VisibleForTesting
    fun hasUnsavedChanges(): Boolean {
        if (!collectionHasLoaded()) {
            return false
        }

        // changed note type?
        if (!addNote && currentEditedCard != null) {
            val newNoteType = currentlySelectedNotetype
            val oldNoteType = currentEditedCard!!.noteType(getColUnsafe)
            if (newNoteType != oldNoteType) {
                return true
            }
        }
        // changed deck?
        if (!addNote && currentEditedCard != null && currentEditedCard!!.currentDeckId() != deckId) {
            return true
        }
        // changed fields?
        if (isFieldEdited) {
            for (value in editFields!!) {
                if (value.text.toString() != "") {
                    return true
                }
            }
            return false
        } else {
            return isTagsEdited
        }
        // changed tags?
    }

    private fun collectionHasLoaded(): Boolean = allNoteTypeIds != null

    // ----------------------------------------------------------------------------
    // SAVE NOTE METHODS
    // ----------------------------------------------------------------------------

    @KotlinCleanup("return early and simplify if possible")
    private fun onNoteAdded() {
        var closeEditorAfterSave = false
        var closeIntent: Intent? = null
        changed = true
        sourceText = null
        refreshNoteData(FieldChangeType.refreshWithStickyFields(shouldReplaceNewlines()))
        showSnackbar(TR.addingAdded(), Snackbar.LENGTH_SHORT)

        if (caller == NoteEditorCaller.NOTEEDITOR || aedictIntent) {
            closeEditorAfterSave = true
        } else if (caller == NoteEditorCaller.NOTEEDITOR_INTENT_ADD) {
            closeEditorAfterSave = true
            closeIntent = Intent().apply { putExtra(EXTRA_ID, requireArguments().getString(EXTRA_ID)) }
        } else if (!editFields!!.isEmpty()) {
            editFields!!.first().focusWithKeyboard()
        }

        if (closeEditorAfterSave) {
            if (caller == NoteEditorCaller.NOTEEDITOR_INTENT_ADD || aedictIntent) {
                showThemedToast(requireContext(), R.string.note_message, shortLength = true)
            }
            closeNoteEditor(closeIntent ?: Intent())
        } else {
            // Reset check for changes to fields
            isFieldEdited = false
            isTagsEdited = false
        }
    }

    private suspend fun saveNoteWithProgress() {
        // adding current note to collection
        requireActivity().withProgress(resources.getString(R.string.saving_facts)) {
            undoableOp {
                notetypes.save(editorNote!!.notetype)
                addNote(editorNote!!, deckId)
            }
        }
        // update UI based on the result, noOfAddedCards
        onNoteAdded()
        updateFieldsFromStickyText()
    }

    @VisibleForTesting
    @NeedsTest("14664: 'first field must not be empty' no longer applies after saving the note")
    @KotlinCleanup("fix !! on oldNoteType/newNoteType")
    suspend fun saveNote() {
        val res = resources
        if (selectedTags == null) {
            selectedTags = ArrayList(0)
        }
        saveToggleStickyMap()

        // treat add new note and edit existing note independently
        if (addNote) {
            // load all of the fields into the note
            for (f in editFields!!) {
                updateField(f)
            }
            // Save deck to noteType
            Timber.d("setting 'last deck' of note type %s to %d", editorNote!!.notetype.name, deckId)
            editorNote!!.notetype.did = deckId
            // Save tags to model
            editorNote!!.setTagsFromStr(getColUnsafe, tagsAsString(selectedTags!!))
            val tags = JSONArray()
            for (t in selectedTags!!) {
                tags.put(t)
            }

            reloadRequired = true

            lifecycleScope.launch {
                val noteFieldsCheck = checkNoteFieldsResponse(editorNote!!)
                if (noteFieldsCheck is NoteFieldsCheckResult.Failure) {
                    addNoteErrorMessage = noteFieldsCheck.localizedMessage ?: getString(R.string.something_wrong)
                    displayErrorSavingNote()
                    return@launch
                }
                addNoteErrorMessage = null
                saveNoteWithProgress()
            }
        } else {
            // Check whether note type has been changed
            val newNoteType = currentlySelectedNotetype
            val oldNoteType = currentEditedCard?.noteType(getColUnsafe)
            if (newNoteType?.id != oldNoteType?.id) {
                reloadRequired = true
                if (noteTypeChangeCardMap!!.size < editorNote!!.numberOfCards(getColUnsafe) ||
                    noteTypeChangeCardMap!!.containsValue(
                        null,
                    )
                ) {
                    // If cards will be lost via the new mapping then show a confirmation dialog before proceeding with the change
                    val dialog = ConfirmationDialog()
                    dialog.setArgs(res.getString(R.string.confirm_map_cards_to_nothing))
                    val confirm =
                        Runnable {
                            // Bypass the check once the user confirms
                            changeNoteType(oldNoteType!!, newNoteType!!)
                        }
                    dialog.setConfirm(confirm)
                    showDialogFragment(dialog)
                } else {
                    // Otherwise go straight to changing note type
                    changeNoteType(oldNoteType!!, newNoteType!!)
                }
                return
            }
            // Regular changes in note content
            var modified = false
            // changed did? this has to be done first as remFromDyn() involves a direct write to the database
            if (currentEditedCard != null && currentEditedCard!!.currentDeckId() != deckId) {
                reloadRequired = true
                undoableOp { setDeck(listOf(currentEditedCard!!.id), deckId) }
                // refresh the card object to reflect the database changes from above
                currentEditedCard!!.load(getColUnsafe)
                // also reload the note object
                editorNote = currentEditedCard!!.note(getColUnsafe)
                // then set the card ID to the new deck
                currentEditedCard!!.did = deckId
                modified = true
                Timber.d("deck ID updated to '%d'", deckId)
            }
            // now load any changes to the fields from the form
            for (f in editFields!!) {
                modified = modified or updateField(f)
            }
            // added tag?
            for (t in selectedTags!!) {
                modified = modified || !editorNote!!.hasTag(getColUnsafe, tag = t)
            }
            // removed tag?
            modified = modified || editorNote!!.tags.size > selectedTags!!.size

            if (!modified) {
                closeNoteEditor()
                return
            }

            editorNote!!.setTagsFromStr(getColUnsafe, tagsAsString(selectedTags!!))
            changed = true

            // these activities are updated to handle `opChanges`
            // and no longer using the legacy ActivityResultCallback/onActivityResult to
            // accept & update the note in the activity
            if (caller == NoteEditorCaller.PREVIEWER_EDIT || caller == NoteEditorCaller.EDIT) {
                requireActivity().withProgress {
                    undoableOp {
                        updateNote(currentEditedCard!!.note(this@undoableOp))
                    }
                }
            }
            closeNoteEditor()
            return
        }
    }

    /**
     * Change the note type from oldNoteType to newNoteType, handling the case where a full sync will be required
     */
    @NeedsTest("test changing note type")
    private fun changeNoteType(
        oldNotetype: NotetypeJson,
        newNotetype: NotetypeJson,
    ) = launchCatchingTask {
        if (!requireAnkiActivity().userAcceptsSchemaChange()) return@launchCatchingTask

        val noteId = editorNote!!.id
        undoableOp {
            notetypes.change(oldNotetype, noteId, newNotetype, noteTypeChangeFieldMap!!, noteTypeChangeCardMap!!)
        }
        // refresh the note object to reflect the database changes
        withCol { editorNote!!.load(this@withCol) }
        // close note editor
        closeNoteEditor()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateToolbar()
    }

    override fun onCreateMenu(
        menu: Menu,
        menuInflater: MenuInflater,
    ) {
        menuInflater.inflate(R.menu.note_editor, menu)
        onPrepareMenu(menu)
    }

    /**
     * Configures the main toolbar with the appropriate menu items and their visibility based on the current state.
     */
    override fun onPrepareMenu(menu: Menu) {
        if (addNote) {
            menu.findItem(R.id.action_copy_note).isVisible = false
            val iconVisible = allowSaveAndPreview()
            menu.findItem(R.id.action_save).isVisible = iconVisible
            menu.findItem(R.id.action_preview).isVisible = iconVisible
        } else {
            // Hide add note item if fragment is in fragmented activity
            // because this item is already present in CardBrowser
            menu.findItem(R.id.action_add_note_from_note_editor).isVisible = !inFragmentedActivity
        }
        if (editFields != null) {
            for (i in editFields!!.indices) {
                val fieldText = editFields!![i].text
                if (!fieldText.isNullOrEmpty()) {
                    menu.findItem(R.id.action_copy_note).isEnabled = true
                    break
                } else if (i == editFields!!.size - 1) {
                    menu.findItem(R.id.action_copy_note).isEnabled = false
                }
            }
        }
        menu.findItem(R.id.action_show_toolbar).isChecked =
            !shouldHideToolbar()
        menu.findItem(R.id.action_capitalize).isChecked =
            sharedPrefs().getBoolean(PREF_NOTE_EDITOR_CAPITALIZE, true)
        menu.findItem(R.id.action_scroll_toolbar).isChecked =
            sharedPrefs().getBoolean(PREF_NOTE_EDITOR_SCROLL_TOOLBAR, true)
    }

    /**
     * When using the options such as image occlusion we don't need the menu's save/preview
     * option to save/preview the card as it has a built in option and the user is notified
     * when the card is saved successfully
     */
    private fun allowSaveAndPreview(): Boolean =
        when {
            addNote && currentNotetypeIsImageOcclusion() -> false
            else -> true
        }

    override fun onMenuItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_preview -> {
                Timber.i("NoteEditor:: Preview button pressed")
                if (allowSaveAndPreview()) {
                    launchCatchingTask { performPreview() }
                }
                return true
            }
            R.id.action_save -> {
                Timber.i("NoteEditor:: Save note button pressed")
                if (allowSaveAndPreview()) {
                    launchCatchingTask { saveNote() }
                }
                return true
            }
            R.id.action_add_note_from_note_editor -> {
                Timber.i("NoteEditor:: Add Note button pressed")
                addNewNote()
                return true
            }
            R.id.action_copy_note -> {
                Timber.i("NoteEditor:: Copy Note button pressed")
                copyNote()
                return true
            }
            R.id.action_font_size -> {
                Timber.i("NoteEditor:: Font Size button pressed")
                val fontSizeDialog = IntegerDialog()
                fontSizeDialog.setArgs(getString(R.string.menu_font_size), editTextFontSize, 2)
                fontSizeDialog.setCallbackRunnable { fontSizeSp: Int? -> setFontSize(fontSizeSp) }
                showDialogFragment(fontSizeDialog)
                return true
            }
            R.id.action_show_toolbar -> {
                item.isChecked = !item.isChecked
                this.sharedPrefs().edit {
                    putBoolean(PREF_NOTE_EDITOR_SHOW_TOOLBAR, item.isChecked)
                }
                updateToolbar()
            }
            R.id.action_capitalize -> {
                Timber.i("NoteEditor:: Capitalize button pressed. New State: %b", !item.isChecked)
                item.isChecked = !item.isChecked // Needed for Android 9
                toggleCapitalize(item.isChecked)
                return true
            }
            R.id.action_scroll_toolbar -> {
                item.isChecked = !item.isChecked
                this.sharedPrefs().edit {
                    putBoolean(PREF_NOTE_EDITOR_SCROLL_TOOLBAR, item.isChecked)
                }
                updateToolbar()
            }
        }
        return false
    }

    private fun toggleCapitalize(value: Boolean) {
        this.sharedPrefs().edit {
            putBoolean(PREF_NOTE_EDITOR_CAPITALIZE, value)
        }
        for (f in editFields!!) {
            f.setCapitalize(value)
        }
    }

    private fun setFontSize(fontSizeSp: Int?) {
        if (fontSizeSp == null || fontSizeSp <= 0) {
            return
        }
        Timber.i("Setting font size to %d", fontSizeSp)
        this.sharedPrefs().edit { putInt(PREF_NOTE_EDITOR_FONT_SIZE, fontSizeSp) }
        for (f in editFields!!) {
            f.textSize = fontSizeSp.toFloat()
        }
    }

    // Note: We're not being accurate here - the initial value isn't actually what's supplied in the layout.xml
    // So a value of 18sp in the XML won't be 18sp on the TextView, but it's close enough.
    // Values are setFontSize are whole when returned.
    private val editTextFontSize: String
        get() {
            // Note: We're not being accurate here - the initial value isn't actually what's supplied in the layout.xml
            // So a value of 18sp in the XML won't be 18sp on the TextView, but it's close enough.
            // Values are setFontSize are whole when returned.
            val sp = TextViewUtil.getTextSizeSp(editFields!!.first())
            return sp.roundToInt().toString()
        }

    private fun addNewNote() {
        launchNoteEditor(NoteEditorLauncher.AddNote(deckId)) { }
    }

    fun copyNote() {
        launchNoteEditor(NoteEditorLauncher.CopyNote(deckId, fieldsText, selectedTags)) { }
    }

    private fun launchNoteEditor(
        arguments: NoteEditorLauncher,
        intentEnricher: Consumer<Bundle>,
    ) {
        val intent = arguments.toIntent(requireContext())
        val bundle = arguments.toBundle()
        // Mutate event with additional properties
        intentEnricher.accept(bundle)
        requestAddLauncher.launch(intent)
    }

    // ----------------------------------------------------------------------------
    // CUSTOM METHODS
    // ----------------------------------------------------------------------------
    @VisibleForTesting
    @NeedsTest("previewing newlines")
    @NeedsTest("cards with a cloze notetype but no cloze in fields are previewed as empty card")
    @NeedsTest("clozes that don't start at '1' are correctly displayed")
    suspend fun performPreview() {
        val convertNewlines = shouldReplaceNewlines()

        fun String?.toFieldText(): String = NoteService.convertToHtmlNewline(this.toString(), convertNewlines)
        val fields = editFields?.mapTo(mutableListOf()) { it.fieldText.toFieldText() } ?: mutableListOf()
        val tags = selectedTags ?: mutableListOf()

        val ord =
            if (editorNote!!.notetype.isCloze) {
                val tempNote = withCol { Note.fromNotetypeId(this@withCol, editorNote!!.notetype.id) }
                tempNote.fields = fields // makes possible to get the cloze numbers from the fields
                val clozeNumbers = withCol { clozeNumbersInNote(tempNote) }
                if (clozeNumbers.isNotEmpty()) {
                    clozeNumbers.first() - 1
                } else {
                    0
                }
            } else {
                currentEditedCard?.ord ?: 0
            }

        val args =
            TemplatePreviewerArguments(
                notetypeFile = NotetypeFile(requireContext(), editorNote!!.notetype),
                fields = fields,
                tags = tags,
                id = editorNote!!.id,
                ord = ord,
                fillEmpty = false,
            )
        val intent = TemplatePreviewerPage.getIntent(requireContext(), args)
        startActivity(intent)
    }

    private fun setTags(tags: Array<String>) {
        selectedTags = tags.toCollection(ArrayList())
        updateTags()
    }

    private fun closeCardEditorWithCheck() {
        if (hasUnsavedChanges()) {
            showDiscardChangesDialog()
        } else {
            closeNoteEditor()
        }
    }

    private fun showDiscardChangesDialog() {
        DiscardChangesDialog.showDialog(requireContext()) {
            Timber.i("NoteEditor:: OK button pressed to confirm discard changes")
            closeNoteEditor()
        }
    }

    private fun closeNoteEditor(intent: Intent = Intent()) {
        val result: Int =
            if (changed) {
                Activity.RESULT_OK
            } else {
                RESULT_CANCELED
            }
        if (reloadRequired) {
            intent.putExtra(RELOAD_REQUIRED_EXTRA_KEY, true)
        }
        if (changed) {
            intent.putExtra(NOTE_CHANGED_EXTRA_KEY, true)
        }
        closeNoteEditor(result, intent)
    }

    private fun closeNoteEditor(
        result: Int,
        intent: Intent?,
    ) {
        requireActivity().apply {
            if (intent != null) {
                setResult(result, intent)
            } else {
                setResult(result)
            }
            // ensure there are no orphans from possible edit previews
            CardTemplateNotetype.clearTempNoteTypeFiles()

            // Don't close this fragment if it is in fragmented activity
            if (inFragmentedActivity) {
                Timber.i("not closing activity: fragmented")
                return
            }

            Timber.i("Closing note editor")

            // Set the finish animation if there is one on the intent which created the activity
            val animation =
                BundleCompat.getParcelable(
                    requireArguments(),
                    AnkiActivity.FINISH_ANIMATION_EXTRA,
                    ActivityTransitionAnimation.Direction::class.java,
                )
            if (animation != null) {
                requireAnkiActivity().finishWithAnimation(animation)
            } else {
                finish()
            }
        }
    }

    private fun showTagsDialog() {
        val selTags = selectedTags?.let { ArrayList(it) } ?: arrayListOf()
        val dialog =
            with(requireContext()) {
                tagsDialogFactory!!.newTagsDialog().withArguments(
                    context = this,
                    type = TagsDialog.DialogType.EDIT_TAGS,
                    checkedTags = selTags,
                )
            }
        showDialogFragment(dialog)
    }

    override fun onSelectedTags(
        selectedTags: List<String>,
        indeterminateTags: List<String>,
        stateFilter: CardStateFilter,
    ) {
        if (this.selectedTags != selectedTags) {
            isTagsEdited = true
        }
        this.selectedTags = selectedTags as ArrayList<String>?
        updateTags()
    }

    private fun showCardTemplateEditor() {
        val intent = Intent(requireContext(), CardTemplateEditor::class.java)
        // Pass the note type ID
        intent.putExtra("noteTypeId", currentlySelectedNotetype!!.id)
        Timber.d(
            "showCardTemplateEditor() for model %s",
            intent.getLongExtra("noteTypeId", NOT_FOUND_NOTE_TYPE),
        )
        // Also pass the note id and ord if not adding new note
        if (!addNote && currentEditedCard != null) {
            intent.putExtra("noteId", currentEditedCard!!.nid)
            Timber.d("showCardTemplateEditor() with note %s", currentEditedCard!!.nid)
            intent.putExtra("ordId", currentEditedCard!!.ord)
            Timber.d("showCardTemplateEditor() with ord %s", currentEditedCard!!.ord)
        }
        requestTemplateEditLauncher.launch(intent)
    }

    /** Appends a string at the selection point, or appends to the end if not in focus  */
    @VisibleForTesting
    fun insertStringInField(
        fieldEditText: EditText?,
        formattedValue: String?,
    ) {
        if (fieldEditText!!.hasFocus()) {
            // Crashes if start > end, although this is fine for a selection via keyboard.
            val start = fieldEditText.selectionStart
            val end = fieldEditText.selectionEnd
            fieldEditText.text.replace(min(start, end), max(start, end), formattedValue)
        } else {
            fieldEditText.text.append(formattedValue)
        }
    }

    /** Sets EditText at index [fieldIndex]'s text to [newString] */
    @VisibleForTesting
    fun setField(
        fieldIndex: Int,
        newString: String,
    ) {
        clearField(fieldIndex)
        insertStringInField(getFieldForTest(fieldIndex), newString)
    }

    private suspend fun getCurrentMultimediaEditableNote(): MultimediaEditableNote {
        val note = NoteService.createEmptyNote(editorNote!!.notetype)
        val fields = currentFieldStrings.requireNoNulls()
        withCol { NoteService.updateMultimediaNoteFromFields(this@withCol, fields, editorNote!!.noteTypeId, note) }

        return note
    }

    /** Determines whether pasted images should be handled as PNG format. **/
    private suspend fun shouldPasteAsPng() = withCol { config.getBool(ConfigKey.Bool.PASTE_IMAGES_AS_PNG) }

    val currentFields: Fields
        get() = editorNote!!.notetype.fields

    @get:CheckResult
    val currentFieldStrings: Array<String?>
        get() {
            if (editFields == null) {
                return arrayOfNulls(0)
            }
            val ret = arrayOfNulls<String>(editFields!!.size)
            for (i in editFields!!.indices) {
                ret[i] = getCurrentFieldText(i)
            }
            return ret
        }

    private fun populateEditFields(
        type: FieldChangeType,
        editNoteTypeMode: Boolean,
    ) {
        val editLines = fieldState.loadFieldEditLines(type)
        fieldsLayoutContainer!!.removeAllViews()
        customViewIds.clear()
        imageOcclusionButtonsContainer?.isVisible = currentNotetypeIsImageOcclusion()

        val indicesToHide = mutableListOf<Int>()
        if (currentNotetypeIsImageOcclusion()) {
            val occlusionTag = "0"
            val imageTag = "1"
            val fields = currentlySelectedNotetype!!.fields
            for ((i, field) in fields.withIndex()) {
                val tag = field.imageOcclusionTag
                if (tag == occlusionTag || tag == imageTag) {
                    indicesToHide.add(i)
                }
            }
        }

        editFields = LinkedList()

        var previous: FieldEditLine? = null
        customViewIds.ensureCapacity(editLines.size)
        for (i in editLines.indices) {
            val editLineView = editLines[i]
            customViewIds.add(editLineView.id)
            val newEditText = editLineView.editText
            lifecycleScope.launch {
                val pasteAsPng = shouldPasteAsPng()
                newEditText.setPasteListener { editText: EditText?, uri: Uri?, description: ClipDescription? ->
                    onPaste(
                        editText!!,
                        uri!!,
                        description!!,
                        pasteAsPng,
                    )
                }
            }
            editLineView.configureView(
                requireActivity(),
                MEDIA_MIME_TYPES,
                DropHelper.Options
                    .Builder()
                    .setHighlightColor(R.color.material_lime_green_A700)
                    .setHighlightCornerRadiusPx(0)
                    .addInnerEditTexts(newEditText)
                    .build(),
                onReceiveContentListener,
            )
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                if (i == 0) {
                    requireView().findViewById<View>(R.id.note_deck_spinner).nextFocusForwardId = newEditText.id
                }
                if (previous != null) {
                    previous.lastViewInTabOrder.nextFocusForwardId = newEditText.id
                }
            }
            previous = editLineView
            editLineView.enableAnimation = requireAnkiActivity().animationEnabled()

            // Use custom implementation of ActionMode.Callback customize selection and insert menus
            editLineView.setActionModeCallbacks(getActionModeCallback(newEditText, View.generateViewId()))
            editLineView.setHintLocale(getHintLocaleForField(editLineView.name))
            initFieldEditText(newEditText, i, !editNoteTypeMode)
            editFields!!.add(newEditText)
            val prefs = this.sharedPrefs()
            if (prefs.getInt(PREF_NOTE_EDITOR_FONT_SIZE, -1) > 0) {
                newEditText.textSize = prefs.getInt(PREF_NOTE_EDITOR_FONT_SIZE, -1).toFloat()
            }
            newEditText.setCapitalize(prefs.getBoolean(PREF_NOTE_EDITOR_CAPITALIZE, true))
            val mediaButton = editLineView.mediaButton
            val toggleStickyButton = editLineView.toggleSticky
            // Make the icon change between media icon and switch field icon depending on whether editing note type
            if (editNoteTypeMode && allowFieldRemapping()) {
                // Allow remapping if originally more than two fields
                mediaButton.setBackgroundResource(R.drawable.ic_import_export)
                setRemapButtonListener(mediaButton, i)
                toggleStickyButton.setBackgroundResource(0)
            } else if (editNoteTypeMode && !allowFieldRemapping()) {
                mediaButton.setBackgroundResource(0)
                toggleStickyButton.setBackgroundResource(0)
            } else {
                // Use media editor button if not changing note type
                mediaButton.setBackgroundResource(R.drawable.ic_attachment)

                mediaButton.setOnClickListener {
                    showMultimediaBottomSheet()
                    handleMultimediaActions(i)
                }

                if (addNote) {
                    // toggle sticky button
                    toggleStickyButton.setBackgroundResource(R.drawable.ic_baseline_push_pin_24)
                    setToggleStickyButtonListener(toggleStickyButton, i)
                } else {
                    toggleStickyButton.setBackgroundResource(0)
                }
            }
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                previous.lastViewInTabOrder.nextFocusForwardId = R.id.CardEditorTagButton
            }
            mediaButton.contentDescription =
                getString(R.string.multimedia_editor_attach_mm_content, editLineView.name)
            toggleStickyButton.contentDescription =
                getString(R.string.note_editor_toggle_sticky, editLineView.name)

            editLineView.isVisible = i !in indicesToHide
            fieldsLayoutContainer!!.addView(editLineView)
        }
    }

    private fun getActionModeCallback(
        textBox: FieldEditText,
        clozeMenuId: Int,
    ): ActionMode.Callback =
        CustomActionModeCallback(
            isClozeType,
            getString(R.string.multimedia_editor_popup_cloze),
            clozeMenuId,
            onActionItemSelected = { mode, item ->
                if (item.itemId == clozeMenuId) {
                    convertSelectedTextToCloze(textBox, AddClozeType.INCREMENT_NUMBER)
                    mode.finish()
                    true
                } else {
                    false
                }
            },
        )

    @VisibleForTesting
    fun showMultimediaBottomSheet() {
        Timber.d("Showing MultimediaBottomSheet fragment")
        val multimediaBottomSheet = MultimediaBottomSheet()
        multimediaBottomSheet.show(parentFragmentManager, "MultimediaBottomSheet")
    }

    /**
     * Handles user interactions with the multimedia options for a specific field in a note.
     *
     * This method is called when the user interacts with a option that allows them to add multimedia
     * content to a field in a note being edited. It presents a `MultimediaBottomSheet`
     * fragment to the user, which provides options for selecting different multimedia types.
     *
     * @param fieldIndex the index of the field in the note where the multimedia content should be added
     */
    private fun handleMultimediaActions(fieldIndex: Int) {
        // Cancel any existing subscription to avoid duplicate listeners
        multimediaActionJob?.cancel()

        // Based on the type of multimedia action received, perform the corresponding operation
        multimediaActionJob =
            lifecycleScope.launch {
                val note: MultimediaEditableNote = getCurrentMultimediaEditableNote()
                if (note.isEmpty) return@launch

                multimediaViewModel.multimediaAction.first { action ->
                    when (action) {
                        MultimediaBottomSheet.MultimediaAction.SELECT_IMAGE_FILE -> {
                            Timber.i("Selected Image option")
                            val field = ImageField()
                            note.setField(fieldIndex, field)
                            openMultimediaImageFragment(fieldIndex = fieldIndex, field, note)
                        }

                        MultimediaBottomSheet.MultimediaAction.SELECT_AUDIO_FILE -> {
                            Timber.i("Selected audio clip option")
                            val field = MediaClipField()
                            note.setField(fieldIndex, field)
                            val mediaIntent =
                                AudioVideoFragment.getIntent(
                                    requireContext(),
                                    MultimediaActivityExtra(fieldIndex, field, note),
                                    AudioVideoFragment.MediaOption.AUDIO_CLIP,
                                )

                            multimediaFragmentLauncher.launch(mediaIntent)
                        }

                        MultimediaBottomSheet.MultimediaAction.OPEN_DRAWING -> {
                            Timber.i("Selected Drawing option")
                            val field = ImageField()
                            note.setField(fieldIndex, field)

                            val drawingIntent =
                                MultimediaImageFragment.getIntent(
                                    requireContext(),
                                    MultimediaActivityExtra(fieldIndex, field, note),
                                    MultimediaImageFragment.ImageOptions.DRAWING,
                                )

                            multimediaFragmentLauncher.launch(drawingIntent)
                        }

                        MultimediaBottomSheet.MultimediaAction.SELECT_AUDIO_RECORDING -> {
                            Timber.i("Selected audio recording option")
                            val field = AudioRecordingField()
                            note.setField(fieldIndex, field)
                            val audioRecordingIntent =
                                AudioRecordingFragment.getIntent(
                                    requireContext(),
                                    MultimediaActivityExtra(fieldIndex, field, note),
                                )

                            multimediaFragmentLauncher.launch(audioRecordingIntent)
                        }

                        MultimediaBottomSheet.MultimediaAction.SELECT_VIDEO_FILE -> {
                            Timber.i("Selected video clip option")
                            val field = MediaClipField()
                            note.setField(fieldIndex, field)
                            val mediaIntent =
                                AudioVideoFragment.getIntent(
                                    requireContext(),
                                    MultimediaActivityExtra(fieldIndex, field, note),
                                    AudioVideoFragment.MediaOption.VIDEO_CLIP,
                                )

                            multimediaFragmentLauncher.launch(mediaIntent)
                        }

                        MultimediaBottomSheet.MultimediaAction.OPEN_CAMERA -> {
                            Timber.i("Selected Camera option")

                            val field = ImageField()
                            note.setField(fieldIndex, field)
                            val imageIntent =
                                MultimediaImageFragment.getIntent(
                                    requireContext(),
                                    MultimediaActivityExtra(fieldIndex, field, note),
                                    MultimediaImageFragment.ImageOptions.CAMERA,
                                )

                            multimediaFragmentLauncher.launch(imageIntent)
                        }
                    }
                    true
                }
            }
    }

    private fun openMultimediaImageFragment(
        fieldIndex: Int,
        field: IField,
        multimediaNote: IMultimediaEditableNote,
        imageUri: Uri? = null,
    ) {
        val multimediaExtra = MultimediaActivityExtra(fieldIndex, field, multimediaNote, imageUri?.toString())

        val imageIntent =
            MultimediaImageFragment.getIntent(
                requireContext(),
                multimediaExtra,
                MultimediaImageFragment.ImageOptions.GALLERY,
            )

        multimediaFragmentLauncher.launch(imageIntent)
    }

    private fun handleMultimediaResult(extras: Bundle) {
        val index = extras.getInt(MULTIMEDIA_RESULT_FIELD_INDEX)
        val field =
            extras.getSerializableCompat<IField>(MULTIMEDIA_RESULT)
                ?: return

        // Process successful result only if field has data
        if (field.type != EFieldType.TEXT || field.mediaFile != null) {
            addMediaFileToField(index, field)
        } else {
            Timber.i("field imagePath and audioPath are both null")
        }
    }

    /**
     * Adds a media file to a specific field within the currently edited multimedia note.
     *
     * @param index The index of the field within the note to update.
     * @param field The `IField` object representing the media file and its details.
     */
    private fun addMediaFileToField(
        index: Int,
        field: IField,
    ) {
        lifecycleScope.launch {
            val note = getCurrentMultimediaEditableNote()
            note.setField(index, field)
            val fieldEditText = editFields!![index]

            // Import field media
            // This goes before setting formattedValue to update
            // media paths with the checksum when they have the same name
            withCol {
                NoteService.importMediaToDirectory(this, field)
            }

            // Completely replace text for text fields (because current text was passed in)
            val formattedValue = field.formattedValue
            if (field.type === EFieldType.TEXT) {
                fieldEditText.setText(formattedValue)
            } else if (fieldEditText.text != null) {
                insertStringInField(fieldEditText, formattedValue)
            }
            changed = true
        }
    }

    private fun onPaste(
        editText: EditText,
        uri: Uri,
        description: ClipDescription,
        pasteAsPng: Boolean,
    ): Boolean {
        val mediaTag =
            MediaRegistration.onPaste(
                requireContext(),
                uri,
                description,
                pasteAsPng,
                showError = { type -> showSnackbar(type.toHumanReadableString(requireContext())) },
            ) ?: return false

        insertStringInField(editText, mediaTag)
        return true
    }

    @NeedsTest("If a field is sticky after synchronization, the toggleStickyButton should be activated.")
    private fun setToggleStickyButtonListener(
        toggleStickyButton: ImageButton,
        index: Int,
    ) {
        if (currentFields[index].sticky) {
            toggleStickyText.getOrPut(index) { "" }
        }
        if (toggleStickyText[index] == null) {
            toggleStickyButton.background.alpha = 64
        } else {
            toggleStickyButton.background.alpha = 255
        }
        toggleStickyButton.setOnClickListener {
            onToggleStickyText(
                toggleStickyButton,
                index,
            )
        }
    }

    private fun onToggleStickyText(
        toggleStickyButton: ImageButton,
        index: Int,
    ) {
        val updatedStickyState = !currentFields[index].sticky
        currentFields[index].sticky = updatedStickyState
        val text = editFields!![index].fieldText
        if (updatedStickyState) {
            toggleStickyText[index] = text
            toggleStickyButton.background.alpha = 255
            Timber.d("Saved Text:: %s", toggleStickyText[index])
        } else {
            toggleStickyText.remove(index)
            toggleStickyButton.background.alpha = 64
        }
        launchCatchingTask {
            withCol {
                this.notetypes.save(editorNote!!.notetype)
            }
        }
    }

    @NeedsTest("13719: moving from a note type with more fields to one with fewer fields")
    private fun saveToggleStickyMap() {
        for ((key) in toggleStickyText.toMap()) {
            // handle fields for different note type with different size
            if (key < editFields!!.size) {
                toggleStickyText[key] = editFields!![key].fieldText
            } else {
                toggleStickyText.remove(key)
            }
        }
    }

    private fun updateFieldsFromStickyText() {
        loadingStickyFields = true
        for ((key, value) in toggleStickyText) {
            // handle fields for different note type with different size
            if (key < editFields!!.size) {
                editFields!![key].setText(value)
            }
        }
        loadingStickyFields = false
    }

    @VisibleForTesting
    fun clearField(index: Int) {
        setFieldValueFromUi(index, "")
    }

    private fun setRemapButtonListener(
        remapButton: ImageButton?,
        newFieldIndex: Int,
    ) {
        remapButton!!.setOnClickListener { v: View? ->
            Timber.i("NoteEditor:: Remap button pressed for new field %d", newFieldIndex)
            // Show list of fields from the original note which we can map to
            val popup = PopupMenu(requireContext(), v!!)
            val items = editorNote!!.items()
            for (i in items.indices) {
                popup.menu.add(Menu.NONE, i, Menu.NONE, items[i][0])
            }
            // Add "nothing" at the end of the list
            popup.menu.add(Menu.NONE, items.size, Menu.NONE, R.string.nothing)
            popup.setOnMenuItemClickListener { item: MenuItem ->
                // Get menu item id
                val idx = item.itemId
                Timber.i("NoteEditor:: User chose to remap to old field %d", idx)
                // Retrieve any existing mappings between newFieldIndex and idx
                val previousMapping = MapUtil.getKeyByValue(noteTypeChangeFieldMap!!, newFieldIndex)
                val mappingConflict = noteTypeChangeFieldMap!![idx]
                // Update the mapping depending on any conflicts
                if (idx == items.size && previousMapping != null) {
                    // Remove the previous mapping if None selected
                    noteTypeChangeFieldMap!!.remove(previousMapping)
                } else if (idx < items.size && mappingConflict != null && previousMapping != null && newFieldIndex != mappingConflict) {
                    // Swap the two mappings if there was a conflict and previous mapping
                    noteTypeChangeFieldMap!![previousMapping] = mappingConflict
                    noteTypeChangeFieldMap!![idx] = newFieldIndex
                } else if (idx < items.size && mappingConflict != null) {
                    // Set the conflicting field to None if no previous mapping to swap into it
                    noteTypeChangeFieldMap!!.remove(previousMapping)
                    noteTypeChangeFieldMap!![idx] = newFieldIndex
                } else if (idx < items.size) {
                    // Can simply set the new mapping if no conflicts
                    noteTypeChangeFieldMap!![idx] = newFieldIndex
                }
                // Reload the fields
                updateFieldsFromMap(currentlySelectedNotetype)
                true
            }
            popup.show()
        }
    }

    private fun initFieldEditText(
        editText: FieldEditText?,
        index: Int,
        enabled: Boolean,
    ) {
        // Listen for changes in the first field so we can re-check duplicate status.
        editText!!.addTextChangedListener(EditFieldTextWatcher(index))
        if (index == 0) {
            editText.onFocusChangeListener =
                OnFocusChangeListener { _: View?, hasFocus: Boolean ->
                    try {
                        if (hasFocus) {
                            // we only want to decorate when we lose focus
                            return@OnFocusChangeListener
                        }
                        @SuppressLint("CheckResult")
                        val currentFieldStrings = currentFieldStrings
                        if (currentFieldStrings.size != 2 || currentFieldStrings[1]!!.isNotEmpty()) {
                            // we only decorate on 2-field cards while second field is still empty
                            return@OnFocusChangeListener
                        }
                        val firstField = currentFieldStrings[0]
                        val decoratedText = NoteFieldDecorator.aplicaHuevo(firstField)
                        if (decoratedText != firstField) {
                            // we only apply the decoration if it is actually different from the first field
                            setFieldValueFromUi(1, decoratedText)
                        }
                    } catch (e: Exception) {
                        Timber.w(e, "Unable to decorate text field")
                    }
                }
        }

        // Sets the background color of disabled EditText.
        if (!enabled) {
            editText.setBackgroundColor(
                MaterialColors.getColor(
                    requireContext(),
                    R.attr.editTextBackgroundColor,
                    0,
                ),
            )
        }
        editText.isEnabled = enabled
    }

    @KotlinCleanup("make name non-null in FieldEditLine")
    private fun getHintLocaleForField(name: String?): Locale? = getFieldByName(name)?.languageHint

    private fun getFieldByName(name: String?): Field? {
        val pair: Pair<Int, Field>? =
            try {
                Notetypes.fieldMap(currentlySelectedNotetype!!)[name]
            } catch (_: Exception) {
                Timber.w("Failed to obtain field '%s'", name)
                return null
            }
        return pair?.second
    }

    private fun setEditFieldTexts(contents: String?) {
        var fields: List<String>? = null
        val len: Int
        if (contents == null) {
            len = 0
        } else {
            fields = Utils.splitFields(contents)
            len = fields.size
        }
        for (i in editFields!!.indices) {
            if (i < len) {
                editFields!![i].setText(fields!![i])
            } else {
                editFields!![i].setText("")
            }
        }
    }

    private fun setDuplicateFieldStyles() {
        // #15579 can be null if switching between two image occlusion types
        if (editFields == null) return
        val field = editFields!![0]
        // Keep copy of current internal value for this field.
        val oldValue = editorNote!!.fields[0]
        // Update the field in the Note so we can run a dupe check on it.
        updateField(field)
        // 1 is empty, 2 is dupe, null is neither.
        val dupeCode = editorNote!!.fieldsCheck(getColUnsafe)
        // Change bottom line color of text field
        if (dupeCode == NoteFieldsCheckResponse.State.DUPLICATE) {
            field.setDupeStyle()
        } else {
            field.setDefaultStyle()
        }
        // Put back the old value so we don't interfere with modification detection
        editorNote!!.values()[0] = oldValue
    }

    @KotlinCleanup("remove 'requireNoNulls'")
    val fieldsText: String
        get() {
            val fields = arrayOfNulls<String>(editFields!!.size)
            for (i in editFields!!.indices) {
                fields[i] = getCurrentFieldText(i)
            }
            return Utils.joinFields(fields.requireNoNulls())
        }

    /** Returns the value of the field at the given index  */
    private fun getCurrentFieldText(index: Int): String {
        val fieldText = editFields!![index].text ?: return ""
        return fieldText.toString()
    }

    private fun setDid(note: Note?) {
        fun calculateDeckId(): DeckId {
            if (deckId != 0L) return deckId
            if (note != null && !addNote && currentEditedCard != null) {
                return currentEditedCard!!.currentDeckId()
            }

            if (!getColUnsafe.config.getBool(ConfigKey.Bool.ADDING_DEFAULTS_TO_CURRENT_DECK)) {
                return getColUnsafe.notetypes.current().let {
                    Timber.d("Adding to deck of note type, noteType: %s", it.name)
                    return@let it.did
                }
            }

            val currentDeckId = getColUnsafe.config.get(CURRENT_DECK) ?: 1L
            return if (getColUnsafe.decks.isFiltered(currentDeckId)) {
                /*
                 * If the deck in mCurrentDid is a filtered (dynamic) deck, then we can't create cards in it,
                 * and we set mCurrentDid to the Default deck. Otherwise, we keep the number that had been
                 * selected previously in the activity.
                 */
                1
            } else {
                currentDeckId
            }
        }

        deckId = calculateDeckId()
        launchCatchingTask { deckSpinnerSelection!!.selectDeckById(deckId, false) }
    }

    /** Refreshes the UI using the currently selected note type as a template  */
    private fun refreshNoteData(changeType: FieldChangeType) {
        setNote(null, changeType)
    }

    /** Handles setting the current note (non-null afterwards) and rebuilding the UI based on this note  */
    private fun setNote(
        note: Note?,
        changeType: FieldChangeType,
    ) {
        editorNote =
            if (note == null || addNote) {
                getColUnsafe.run {
                    val notetype = notetypes.current()
                    Note.fromNotetypeId(this@run, notetype.id)
                }
            } else {
                note
            }
        if (selectedTags == null) {
            selectedTags = editorNote!!.tags
        }
        // nb: setOnItemSelectedListener and populateEditFields need to occur after this
        setNoteTypePosition()
        setDid(note)
        updateTags()
        updateCards(editorNote!!.notetype)
        updateToolbar()
        populateEditFields(changeType, false)
        updateFieldsFromStickyText()
    }

    private fun addClozeButton(
        @DrawableRes drawableRes: Int,
        description: String,
        type: AddClozeType,
    ) {
        val drawable =
            ResourcesCompat.getDrawable(resources, drawableRes, null)!!.apply {
                setTint(MaterialColors.getColor(requireContext(), R.attr.toolbarIconColor, 0))
            }
        val button =
            toolbar.insertItem(0, drawable) { insertCloze(type) }.apply {
                contentDescription = description
            }
        button.setTooltipTextCompat(description)
    }

    private fun updateToolbar() {
        val editorLayout = requireView().findViewById<View>(R.id.note_editor_layout)
        val bottomMargin =
            if (shouldHideToolbar()) {
                0
            } else {
                resources
                    .getDimension(R.dimen.note_editor_toolbar_height)
                    .toInt()
            }
        val params = editorLayout.layoutParams as MarginLayoutParams
        params.bottomMargin = bottomMargin
        editorLayout.layoutParams = params
        if (shouldHideToolbar()) {
            toolbar.visibility = View.GONE
            return
        } else {
            toolbar.visibility = View.VISIBLE
        }
        toolbar.clearCustomItems()
        if (editorNote!!.notetype.isCloze) {
            addClozeButton(
                drawableRes = R.drawable.ic_cloze_new_card,
                description = TR.editingClozeDeletion(),
                type = AddClozeType.INCREMENT_NUMBER,
            )
            addClozeButton(
                drawableRes = R.drawable.ic_cloze_same_card,
                description = TR.editingClozeDeletionRepeat(),
                type = AddClozeType.SAME_NUMBER,
            )
        }
        val buttons = toolbarButtons
        for (b in buttons) {
            // 0th button shows as '1' and is Ctrl + 1
            val visualIndex = b.index + 1
            var text = visualIndex.toString()
            if (b.buttonText.isNotEmpty()) {
                text = b.buttonText
            }
            val bmp = toolbar.createDrawableForString(text)

            val v =
                toolbar.insertItem(0, bmp) {
                    // Attempt to open keyboard for the currently focused view in the hosting Activity
                    val activity = context as? Activity
                    activity.showSoftInput()

                    toolbar.onFormat(b.toFormatter())
                }
            v.contentDescription = text

            // Allow Ctrl + 1...Ctrl + 0 for item 10.
            v.tag = (visualIndex % 10).toString()
            v.setOnContextAndLongClickListener {
                displayEditToolbarDialog(b)
                true
            }
        }

        // Let the user add more buttons (always at the end).
        // Sets the add custom tag icon color.
        val drawable = ResourcesCompat.getDrawable(resources, R.drawable.ic_add_toolbar_icon, null)
        drawable!!.setTint(MaterialColors.getColor(requireContext(), R.attr.toolbarIconColor, 0))
        val addButton = toolbar.insertItem(0, drawable) { displayAddToolbarDialog() }
        addButton.contentDescription = resources.getString(R.string.add_toolbar_item)
        addButton.setTooltipTextCompat(resources.getString(R.string.add_toolbar_item))
    }

    private val toolbarButtons: ArrayList<CustomToolbarButton>
        get() {
            val set =
                this
                    .sharedPrefs()
                    .getStringSet(PREF_NOTE_EDITOR_CUSTOM_BUTTONS, HashUtil.hashSetInit(0))
            return CustomToolbarButton.fromStringSet(set!!)
        }

    private fun saveToolbarButtons(buttons: ArrayList<CustomToolbarButton>) {
        this.sharedPrefs().edit {
            putStringSet(PREF_NOTE_EDITOR_CUSTOM_BUTTONS, CustomToolbarButton.toStringSet(buttons))
        }
    }

    private fun addToolbarButton(
        buttonText: String,
        prefix: String,
        suffix: String,
    ) {
        if (prefix.isEmpty() && suffix.isEmpty()) return
        val toolbarButtons = toolbarButtons
        toolbarButtons.add(CustomToolbarButton(toolbarButtons.size, buttonText, prefix, suffix))
        saveToolbarButtons(toolbarButtons)
        updateToolbar()
    }

    private fun editToolbarButton(
        buttonText: String,
        prefix: String,
        suffix: String,
        currentButton: CustomToolbarButton,
    ) {
        val toolbarButtons = toolbarButtons
        val currentButtonIndex = currentButton.index

        toolbarButtons[currentButtonIndex] =
            CustomToolbarButton(
                index = currentButtonIndex,
                buttonText = buttonText.ifEmpty { currentButton.buttonText },
                prefix = prefix.ifEmpty { currentButton.prefix },
                suffix = suffix.ifEmpty { currentButton.suffix },
            )

        saveToolbarButtons(toolbarButtons)
        updateToolbar()
    }

    private fun suggestRemoveButton(
        button: CustomToolbarButton,
        editToolbarItemDialog: AlertDialog,
    ) {
        AlertDialog.Builder(requireContext()).show {
            title(R.string.remove_toolbar_item)
            positiveButton(R.string.dialog_positive_delete) {
                editToolbarItemDialog.dismiss()
                removeButton(button)
            }
            negativeButton(R.string.dialog_cancel)
        }
    }

    private fun removeButton(button: CustomToolbarButton) {
        val toolbarButtons = toolbarButtons
        toolbarButtons.removeAt(button.index)
        saveToolbarButtons(toolbarButtons)
        updateToolbar()
    }

    private val toolbarDialog: AlertDialog.Builder
        get() =
            AlertDialog
                .Builder(requireContext())
                .neutralButton(R.string.help) {
                    requireAnkiActivity().openUrl(getString(R.string.link_manual_note_format_toolbar).toUri())
                }.negativeButton(R.string.dialog_cancel)

    private fun displayAddToolbarDialog() {
        val v = layoutInflater.inflate(R.layout.note_editor_toolbar_add_custom_item, null)
        toolbarDialog.show {
            title(R.string.add_toolbar_item)
            setView(v)
            positiveButton(R.string.dialog_positive_create) {
                val etIcon = v.findViewById<EditText>(R.id.note_editor_toolbar_item_icon)
                val et = v.findViewById<EditText>(R.id.note_editor_toolbar_before)
                val et2 = v.findViewById<EditText>(R.id.note_editor_toolbar_after)
                addToolbarButton(etIcon.text.toString(), et.text.toString(), et2.text.toString())
            }
        }
    }

    private fun displayEditToolbarDialog(currentButton: CustomToolbarButton) {
        val view = layoutInflater.inflate(R.layout.note_editor_toolbar_edit_custom_item, null)
        val etIcon = view.findViewById<EditText>(R.id.note_editor_toolbar_item_icon)
        val et = view.findViewById<EditText>(R.id.note_editor_toolbar_before)
        val et2 = view.findViewById<EditText>(R.id.note_editor_toolbar_after)
        val btnDelete = view.findViewById<ImageButton>(R.id.note_editor_toolbar_btn_delete)
        etIcon.setText(currentButton.buttonText)
        et.setText(currentButton.prefix)
        et2.setText(currentButton.suffix)
        val editToolbarDialog =
            toolbarDialog
                .setView(view)
                .positiveButton(R.string.save) {
                    editToolbarButton(
                        etIcon.text.toString(),
                        et.text.toString(),
                        et2.text.toString(),
                        currentButton,
                    )
                }.create()
        btnDelete.setOnClickListener {
            suggestRemoveButton(
                currentButton,
                editToolbarDialog,
            )
        }
        editToolbarDialog.show()
    }

    private fun setNoteTypePosition() {
        // Set current note type and deck positions in spinners
        val position = allNoteTypeIds!!.indexOf(editorNote!!.notetype.id)
        // set selection without firing selectionChanged event
        noteTypeSpinner!!.setSelection(position, false)
    }

    override val shortcuts
        get() =
            ShortcutGroup(
                listOf(
                    shortcut("Ctrl+ENTER") { getString(R.string.save) },
                    shortcut("Ctrl+D") { getString(R.string.select_deck) },
                    shortcut("Ctrl+L") { getString(R.string.card_template_editor_group) },
                    shortcut("Ctrl+N") { getString(R.string.select_note_type) },
                    shortcut("Ctrl+Shift+T") { getString(R.string.tag_editor) },
                    shortcut("Ctrl+Shift+C") { getString(R.string.multimedia_editor_popup_cloze) },
                    shortcut("Ctrl+P") { getString(R.string.card_editor_preview_card) },
                ),
                R.string.note_editor_group,
            )

    private fun updateTags() {
        if (selectedTags == null) {
            selectedTags = ArrayList(0)
        }
        tagsButton!!.text =
            resources.getString(
                R.string.CardEditorTags,
                getColUnsafe.tags
                    .join(getColUnsafe.tags.canonify(selectedTags!!))
                    .trim()
                    .replace(" ", ", "),
            )
    }

    /** Update the list of card templates for current note type  */
    @KotlinCleanup("make non-null")
    private fun updateCards(noteType: NotetypeJson?) {
        Timber.d("updateCards()")
        val tmpls = noteType!!.templates
        var cardsList = StringBuilder()
        // Build comma separated list of card names
        Timber.d("updateCards() template count is %s", tmpls.length())
        for ((i, tmpl) in tmpls.withIndex()) {
            var name = tmpl.jsonObject.optString("name")
            // If more than one card, and we have an existing card, underline existing card
            if (!addNote &&
                tmpls.length() > 1 &&
                noteType.jsonObject === editorNote!!.notetype.jsonObject &&
                currentEditedCard != null &&
                currentEditedCard!!.template(getColUnsafe).jsonObject.optString("name") == name
            ) {
                name = "<u>$name</u>"
            }
            cardsList.append(name)
            if (i < tmpls.length() - 1) {
                cardsList.append(", ")
            }
        }
        // Make cards list red if the number of cards is being reduced
        if (!addNote && tmpls.length() < editorNote!!.notetype.templates.length()) {
            cardsList = StringBuilder("<font color='red'>$cardsList</font>")
        }
        cardsButton!!.text =
            HtmlCompat.fromHtml(
                resources.getString(R.string.CardEditorCards, cardsList.toString()),
                HtmlCompat.FROM_HTML_MODE_LEGACY,
            )
    }

    private fun updateField(field: FieldEditText?): Boolean {
        val fieldContent = field!!.text?.toString() ?: ""
        val correctedFieldContent = NoteService.convertToHtmlNewline(fieldContent, shouldReplaceNewlines())
        if (editorNote!!.values()[field.ord] != correctedFieldContent) {
            editorNote!!.values()[field.ord] = correctedFieldContent
            return true
        }
        return false
    }

    private fun tagsAsString(tags: List<String>): String = tags.joinToString(" ")

    private val currentlySelectedNotetype: NotetypeJson?
        get() =
            noteTypeSpinner?.selectedItemPosition?.let { position ->
                allNoteTypeIds?.get(position)?.let { noteTypeId ->
                    getColUnsafe.notetypes.get(noteTypeId)
                }
            }

    /**
     * Update all the field EditText views based on the currently selected note type and the noteTypeChangeFieldMap
     */
    private fun updateFieldsFromMap(newNotetype: NotetypeJson?) {
        val type = FieldChangeType.refreshWithMap(newNotetype, noteTypeChangeFieldMap, shouldReplaceNewlines())
        populateEditFields(type, true)
        updateCards(newNotetype)
    }

    /**
     *
     * @return whether or not to allow remapping of fields for current note type
     */
    private fun allowFieldRemapping(): Boolean {
        // Map<String, Pair<Integer, JSONObject>> fMapNew = getCol().getNoteTypes().fieldMap(getCurrentlySelectedNoteType())
        return editorNote!!.items().size > 2
    }

    val fieldsFromSelectedNote: Array<Array<String>>
        get() = editorNote!!.items()

    private fun currentNotetypeIsImageOcclusion() = currentlySelectedNotetype?.isImageOcclusion == true

    private fun setupImageOcclusionEditor(imagePath: String = "") {
        val kind: String
        val id: Long
        if (addNote) {
            kind = "add"
            // if opened from an intent, the selected note type may not be suitable for IO
            id =
                if (currentNotetypeIsImageOcclusion()) {
                    currentlySelectedNotetype!!.id
                } else {
                    0
                }
        } else {
            kind = "edit"
            id = editorNote?.id!!
        }
        val intent = ImageOcclusion.getIntent(requireContext(), kind, id, imagePath, deckId)
        requestIOEditorCloser.launch(intent)
    }

    private fun changeNoteType(newId: NoteTypeId) {
        val oldNoteTypeId = getColUnsafe.notetypes.current().id
        Timber.i("Changing note type to '%d", newId)

        if (oldNoteTypeId == newId) {
            return
        }

        val noteType = getColUnsafe.notetypes.get(newId)
        if (noteType == null) {
            Timber.w("New note type %s not found, not changing note type", newId)
            return
        }

        getColUnsafe.notetypes.setCurrent(noteType)
        val currentDeck = getColUnsafe.decks.current()
        currentDeck.put("mid", newId)
        getColUnsafe.decks.save(currentDeck)

        // Update deck
        if (!getColUnsafe.config.getBool(ConfigKey.Bool.ADDING_DEFAULTS_TO_CURRENT_DECK)) {
            deckId = noteType.did
        }

        refreshNoteData(FieldChangeType.changeFieldCount(shouldReplaceNewlines()))
        setDuplicateFieldStyles()
        deckSpinnerSelection!!.updateDeckPosition(deckId)
    }

    // ----------------------------------------------------------------------------
    // INNER CLASSES
    // ----------------------------------------------------------------------------
    private inner class SetNoteTypeListener : OnItemSelectedListener {
        override fun onItemSelected(
            parent: AdapterView<*>?,
            view: View?,
            pos: Int,
            id: Long,
        ) {
            // If a new column was selected then change the key used to map from mCards to the column TextView
            // Timber.i("NoteEditor:: onItemSelected() fired on mNoteTypeSpinner");
            // In case the type is changed while adding the card, the menu options need to be invalidated
            requireActivity().invalidateMenu()
            changeNoteType(allNoteTypeIds!![pos])
        }

        override fun onNothingSelected(parent: AdapterView<*>?) {
            // Do Nothing
        }
    }

    // Uses only if mCurrentEditedCard is set, so from reviewer or card browser.
    private inner class EditNoteTypeListener : OnItemSelectedListener {
        override fun onItemSelected(
            parent: AdapterView<*>?,
            view: View?,
            pos: Int,
            id: Long,
        ) {
            // Get the current model
            val noteNoteTypeId = currentEditedCard!!.noteType(getColUnsafe).id
            // Get new model
            val newNoteType = getColUnsafe.notetypes.get(allNoteTypeIds!![pos])
            if (newNoteType == null) {
                Timber.w("newModel %s not found", allNoteTypeIds!![pos])
                return
            }
            // Configure the interface according to whether note type is getting changed or not
            if (allNoteTypeIds!![pos] != noteNoteTypeId) {
                @KotlinCleanup("Check if this ever happens")
                val tmpls =
                    try {
                        newNoteType.templates
                    } catch (_: Exception) {
                        Timber.w("error in obtaining templates from note type %s", allNoteTypeIds!![pos])
                        return
                    }
                // Initialize mapping between fields of old note type -> new note type
                val itemsLength = editorNote!!.items().size
                noteTypeChangeFieldMap = HashUtil.hashMapInit(itemsLength)
                for (i in 0 until itemsLength) {
                    noteTypeChangeFieldMap!![i] = i
                }
                // Initialize mapping between cards new note type -> old note type
                val templatesLength = tmpls.length()
                noteTypeChangeCardMap = HashUtil.hashMapInit(templatesLength)
                for (i in 0 until templatesLength) {
                    if (i < editorNote!!.numberOfCards(getColUnsafe)) {
                        noteTypeChangeCardMap!![i] = i
                    } else {
                        noteTypeChangeCardMap!![i] = null
                    }
                }
                // Update the field text edits based on the default mapping just assigned
                updateFieldsFromMap(newNoteType)
                // Don't let the user change any other values at the same time as changing note type
                selectedTags = editorNote!!.tags
                updateTags()
                requireView().findViewById<View>(R.id.CardEditorTagButton).isEnabled = false
                // ((LinearLayout) findViewById(R.id.CardEditorCardsButton)).setEnabled(false);
                deckSpinnerSelection!!.setEnabledActionBarSpinner(false)
                deckSpinnerSelection!!.updateDeckPosition(currentEditedCard!!.did)
                updateFieldsFromStickyText()
            } else {
                populateEditFields(FieldChangeType.refresh(shouldReplaceNewlines()), false)
                updateCards(currentEditedCard!!.noteType(getColUnsafe))
                requireView().findViewById<View>(R.id.CardEditorTagButton).isEnabled = true
                // ((LinearLayout) findViewById(R.id.CardEditorCardsButton)).setEnabled(false);
                deckSpinnerSelection!!.setEnabledActionBarSpinner(true)
            }
        }

        override fun onNothingSelected(parent: AdapterView<*>?) {
            // Do Nothing
        }
    }

    private fun convertSelectedTextToCloze(
        textBox: FieldEditText,
        addClozeType: AddClozeType,
    ) {
        var nextClozeIndex = nextClozeIndex
        if (addClozeType == AddClozeType.SAME_NUMBER) {
            nextClozeIndex -= 1
        }
        val prefix = "{{c" + max(1, nextClozeIndex) + "::"
        val suffix = "}}"
        modifyCurrentSelection(TextWrapper(prefix, suffix), textBox)
    }

    // BUG: This assumes all fields are inserted as: {{cloze:Text}}
    private val nextClozeIndex: Int
        get() {
            // BUG: This assumes all fields are inserted as: {{cloze:Text}}
            val fieldValues: MutableList<String> =
                ArrayList(
                    editFields!!.size,
                )
            for (e in editFields!!) {
                val editable = e.text
                val fieldValue = editable?.toString() ?: ""
                fieldValues.add(fieldValue)
            }
            return ClozeUtils.getNextClozeIndex(fieldValues)
        }
    private val isClozeType: Boolean
        get() = currentlySelectedNotetype!!.isCloze

    @VisibleForTesting
    fun setFieldValueFromUi(
        i: Int,
        newText: String?,
    ) {
        val editText = editFields!![i]
        editText.setText(newText)
        EditFieldTextWatcher(i).afterTextChanged(editText.text!!)
    }

    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    fun getFieldForTest(index: Int): FieldEditText = editFields!![index]

    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    fun setCurrentlySelectedNoteType(noteTypeId: NoteTypeId) {
        val position = allNoteTypeIds!!.indexOf(noteTypeId)
        check(position != -1) { "$noteTypeId not found" }
        noteTypeSpinner!!.setSelection(position)
    }

    /**
     * Whether sticky fields are currently being loaded. In this card, don't consider the text changed.
     */
    private var loadingStickyFields = false

    private inner class EditFieldTextWatcher(
        private val index: Int,
    ) : TextWatcher {
        override fun afterTextChanged(arg0: Editable) {
            if (!loadingStickyFields) {
                isFieldEdited = true
            }
            if (index == 0) {
                setDuplicateFieldStyles()
            }
        }

        override fun beforeTextChanged(
            arg0: CharSequence,
            arg1: Int,
            arg2: Int,
            arg3: Int,
        ) {
            // do nothing
        }

        override fun onTextChanged(
            arg0: CharSequence,
            arg1: Int,
            arg2: Int,
            arg3: Int,
        ) {
            // do nothing
        }
    }

    companion object {
        // DA 2020-04-13 - Refactoring Plans once tested:
        // * There is a difference in functionality depending on whether we are editing
        // * Extract mAddNote and mCurrentEditedCard into inner class. Gate mCurrentEditedCard on edit state.
        // * Possibly subclass
        // * Make this in memory and immutable for metadata, it's hard to reason about our state if we're modifying col
        // * Consider persistence strategy for temporary media. Saving after multimedia edit is probably too early, but
        // we don't want to risk the cache being cleared. Maybe add in functionality to remove from collection if added and
        // the action is cancelled?
        //    public static final String SOURCE_LANGUAGE = "SOURCE_LANGUAGE";
        //    public static final String TARGET_LANGUAGE = "TARGET_LANGUAGE";
        const val SOURCE_TEXT = "SOURCE_TEXT"
        const val TARGET_TEXT = "TARGET_TEXT"
        const val EXTRA_CALLER = "CALLER"
        const val EXTRA_CARD_ID = "CARD_ID"
        const val EXTRA_CONTENTS = "CONTENTS"
        const val EXTRA_TAGS = "TAGS"
        const val EXTRA_ID = "ID"
        const val EXTRA_DID = "DECK_ID"
        const val EXTRA_TEXT_FROM_SEARCH_VIEW = "SEARCH"
        const val EXTRA_EDIT_FROM_CARD_ID = "editCid"
        const val ACTION_CREATE_FLASHCARD = "org.openintents.action.CREATE_FLASHCARD"
        const val ACTION_CREATE_FLASHCARD_SEND = "android.intent.action.SEND"
        const val NOTE_CHANGED_EXTRA_KEY = "noteChanged"
        const val RELOAD_REQUIRED_EXTRA_KEY = "reloadRequired"
        const val EXTRA_IMG_OCCLUSION = "image_uri"
        const val IN_FRAGMENTED_ACTIVITY = "inFragmentedActivity"

        // calling activity
        enum class NoteEditorCaller(
            val value: Int,
        ) {
            NO_CALLER(0),
            EDIT(1),
            STUDYOPTIONS(2),
            DECKPICKER(3),
            REVIEWER_ADD(11),
            CARDBROWSER_ADD(7),
            NOTEEDITOR(8),
            PREVIEWER_EDIT(9),
            NOTEEDITOR_INTENT_ADD(10),
            IMG_OCCLUSION(12),
            ADD_IMAGE(13),
            INSTANT_NOTE_EDITOR(14),
            ;

            companion object {
                fun fromValue(value: Int) = NoteEditorCaller.entries.first { it.value == value }
            }
        }

        const val RESULT_UPDATED_IO_NOTE = 11

        // preferences keys
        const val PREF_NOTE_EDITOR_SCROLL_TOOLBAR = "noteEditorScrollToolbar"
        private const val PREF_NOTE_EDITOR_SHOW_TOOLBAR = "noteEditorShowToolbar"
        private const val PREF_NOTE_EDITOR_NEWLINE_REPLACE = "noteEditorNewlineReplace"
        private const val PREF_NOTE_EDITOR_CAPITALIZE = "note_editor_capitalize"
        private const val PREF_NOTE_EDITOR_FONT_SIZE = "note_editor_font_size"
        private const val PREF_NOTE_EDITOR_CUSTOM_BUTTONS = "note_editor_custom_buttons"

        fun newInstance(launcher: NoteEditorLauncher): NoteEditorFragment =
            NoteEditorFragment().apply {
                this.arguments = launcher.toBundle()
            }

        private fun shouldReplaceNewlines(): Boolean =
            AnkiDroidApp.instance
                .sharedPrefs()
                .getBoolean(PREF_NOTE_EDITOR_NEWLINE_REPLACE, true)

        @VisibleForTesting
        @CheckResult
        fun intentLaunchedWithImage(intent: Intent): Boolean {
            if (intent.action != Intent.ACTION_SEND && intent.action != Intent.ACTION_VIEW) return false
            if (ImportUtils.isInvalidViewIntent(intent)) return false
            return intent.resolveMimeType()?.startsWith("image/") == true
        }

        private fun shouldHideToolbar(): Boolean =
            !AnkiDroidApp.instance
                .sharedPrefs()
                .getBoolean(PREF_NOTE_EDITOR_SHOW_TOOLBAR, true)
    }
}
