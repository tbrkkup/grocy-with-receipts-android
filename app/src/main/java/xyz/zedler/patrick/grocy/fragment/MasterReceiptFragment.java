/*
 * This file is part of Grocy Android.
 *
 * Grocy Android is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Grocy Android is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Grocy Android. If not, see http://www.gnu.org/licenses/.
 *
 * Copyright (c) 2020-2024 by Patrick Zedler and Dominic Zedler
 * Copyright (c) 2024-2026 by Patrick Zedler
 */

package xyz.zedler.patrick.grocy.fragment;

import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.MimeTypeMap;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.preference.PreferenceManager;
import com.android.volley.VolleyError;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONException;
import org.json.JSONObject;
import xyz.zedler.patrick.grocy.Constants;
import xyz.zedler.patrick.grocy.R;
import xyz.zedler.patrick.grocy.activity.MainActivity;
import xyz.zedler.patrick.grocy.api.GrocyApi;
import xyz.zedler.patrick.grocy.api.GrocyApi.ENTITY;
import xyz.zedler.patrick.grocy.behavior.SystemBarBehavior;
import xyz.zedler.patrick.grocy.databinding.FragmentMasterReceiptBinding;
import xyz.zedler.patrick.grocy.helper.DownloadHelper;
import xyz.zedler.patrick.grocy.model.Receipt;
import xyz.zedler.patrick.grocy.model.ReceiptFile;
import xyz.zedler.patrick.grocy.model.Store;
import xyz.zedler.patrick.grocy.util.DateUtil;
import xyz.zedler.patrick.grocy.util.PrefsUtil;

public class MasterReceiptFragment extends BaseFragment {

  private final static String TAG = MasterReceiptFragment.class.getSimpleName();

  private MainActivity activity;
  private Gson gson;
  private GrocyApi grocyApi;
  private DownloadHelper dlHelper;
  private FragmentMasterReceiptBinding binding;

  private List<Store> stores;
  private Receipt editReceipt;
  private String selectedStoreId;
  private String selectedDate;
  private String selectedStatus;

  private ActivityResultLauncher<String> filePickerLauncher;

  private boolean debug;

  @Override
  public View onCreateView(
      @NonNull LayoutInflater inflater,
      ViewGroup container,
      Bundle savedInstanceState
  ) {
    binding = FragmentMasterReceiptBinding.inflate(inflater, container, false);
    return binding.getRoot();
  }

  @Override
  public void onDestroyView() {
    super.onDestroyView();
    binding = null;
    dlHelper.destroy();
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    activity = (MainActivity) requireActivity();
    SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(activity);
    debug = PrefsUtil.isDebuggingEnabled(sharedPrefs);

    dlHelper = new DownloadHelper(activity, TAG);
    grocyApi = activity.getGrocyApi();
    gson = new Gson();

    stores = new ArrayList<>();

    SystemBarBehavior systemBarBehavior = new SystemBarBehavior(activity);
    systemBarBehavior.setAppBar(binding.appBar);
    systemBarBehavior.setContainer(binding.swipe);
    systemBarBehavior.setScroll(binding.scroll, binding.constraint);
    systemBarBehavior.setUp();
    activity.setSystemBarBehavior(systemBarBehavior);

    binding.toolbar.setNavigationOnClickListener(v -> activity.performOnBackPressed());
    binding.swipe.setOnRefreshListener(this::download);

    editReceipt = MasterReceiptFragmentArgs.fromBundle(requireArguments()).getReceipt();
    if (editReceipt != null) {
      selectedStoreId = editReceipt.getShoppingLocationId();
      selectedDate = editReceipt.getDate();
      selectedStatus = editReceipt.getStatus();
    } else {
      selectedStatus = Receipt.STATUS_PAID;
      selectedDate = DateUtil.getDateStringToday();
    }

    binding.editTextStore.setOnClickListener(v -> showStoreDialog());
    binding.editTextDate.setOnClickListener(v -> showDatePicker());
    binding.editTextStatus.setOnClickListener(v -> showStatusDialog());

    filePickerLauncher = registerForActivityResult(
        new ActivityResultContracts.GetContent(),
        uri -> {
          if (uri != null) {
            uploadFile(uri);
          }
        }
    );

    // Files can only be attached once the receipt exists (needs its id)
    binding.linearFilesSection.setVisibility(editReceipt != null ? View.VISIBLE : View.GONE);
    binding.buttonAddFile.setOnClickListener(v -> filePickerLauncher.launch("*/*"));

    fillForm();

    if (savedInstanceState == null) {
      download();
      if (editReceipt != null) {
        loadReceiptFiles();
      }
    }

    activity.getScrollBehavior().setNestedOverScrollFixEnabled(true);
    activity.getScrollBehavior().setUpScroll(
        binding.appBar, false, binding.scroll, true
    );
    activity.getScrollBehavior().setBottomBarVisibility(true);
    activity.updateBottomAppBar(
        true,
        editReceipt != null ? R.menu.menu_master_item_edit : R.menu.menu_empty,
        getBottomMenuClickListener()
    );
    activity.updateFab(
        R.drawable.ic_round_backup,
        R.string.action_save,
        Constants.FAB.TAG.SAVE,
        savedInstanceState == null,
        this::saveReceipt
    );
  }

  private void download() {
    if (!activity.isOnline()) {
      binding.swipe.setRefreshing(false);
      return;
    }
    binding.swipe.setRefreshing(true);
    dlHelper.get(
        grocyApi.getObjects(ENTITY.STORES),
        response -> {
          stores = gson.fromJson(
              response, new TypeToken<ArrayList<Store>>() {
              }.getType()
          );
          binding.swipe.setRefreshing(false);
          fillStoreField();
        },
        error -> {
          binding.swipe.setRefreshing(false);
          activity.showSnackbar(
              activity.getSnackbar(getErrorMessage(error), false).setAction(
                  R.string.action_retry, v1 -> download()
              )
          );
        }
    );
  }

  private void fillForm() {
    fillStoreField();
    binding.editTextDate.setText(selectedDate);
    binding.editTextStatus.setText(getStatusLabel(selectedStatus));
    if (editReceipt != null) {
      binding.editTextDescription.setText(editReceipt.getDescription());
    }
  }

  private void fillStoreField() {
    Store store = getStoreById(selectedStoreId);
    binding.editTextStore.setText(store != null ? store.getName() : null);
  }

  @Nullable
  private Store getStoreById(String id) {
    if (id == null || stores == null) {
      return null;
    }
    for (Store store : stores) {
      if (String.valueOf(store.getId()).equals(id)) {
        return store;
      }
    }
    return null;
  }

  private String getStatusLabel(String status) {
    if (status == null) {
      return null;
    }
    switch (status) {
      case Receipt.STATUS_OPEN:
        return getString(R.string.property_status_open);
      case Receipt.STATUS_REFUNDED:
        return getString(R.string.property_status_refunded);
      default:
        return getString(R.string.property_status_paid);
    }
  }

  private void showStoreDialog() {
    if (stores == null || stores.isEmpty()) {
      return;
    }
    String[] names = new String[stores.size()];
    for (int i = 0; i < stores.size(); i++) {
      names[i] = stores.get(i).getName();
    }
    new MaterialAlertDialogBuilder(activity)
        .setTitle(R.string.property_store)
        .setItems(names, (dialog, which) -> {
          selectedStoreId = String.valueOf(stores.get(which).getId());
          fillStoreField();
        })
        .setNegativeButton(R.string.action_cancel, null)
        .show();
  }

  private void showStatusDialog() {
    String[] statuses = {Receipt.STATUS_PAID, Receipt.STATUS_OPEN, Receipt.STATUS_REFUNDED};
    String[] labels = new String[statuses.length];
    for (int i = 0; i < statuses.length; i++) {
      labels[i] = getStatusLabel(statuses[i]);
    }
    new MaterialAlertDialogBuilder(activity)
        .setTitle(R.string.property_status)
        .setItems(labels, (dialog, which) -> {
          selectedStatus = statuses[which];
          binding.editTextStatus.setText(getStatusLabel(selectedStatus));
        })
        .setNegativeButton(R.string.action_cancel, null)
        .show();
  }

  private void showDatePicker() {
    MaterialDatePicker<Long> picker = MaterialDatePicker.Builder.datePicker()
        .setTitleText(R.string.property_date)
        .setNegativeButtonText(R.string.action_cancel)
        .setPositiveButtonText(R.string.action_save)
        .setTheme(R.style.ThemeOverlay_Grocy_DatePicker)
        .build();
    picker.addOnPositiveButtonClickListener(selection -> {
      selectedDate = DateUtil.DATE_FORMAT.format(selection);
      binding.editTextDate.setText(selectedDate);
    });
    picker.show(getParentFragmentManager(), "date_picker_dialog");
  }

  private void saveReceipt() {
    JSONObject jsonObject = new JSONObject();
    try {
      Editable description = binding.editTextDescription.getText();
      jsonObject.put("status", selectedStatus != null ? selectedStatus : Receipt.STATUS_PAID);
      if (selectedDate != null && !selectedDate.isEmpty()) {
        jsonObject.put("date", selectedDate);
      }
      if (selectedStoreId != null && !selectedStoreId.isEmpty()) {
        jsonObject.put("shopping_location_id", selectedStoreId);
      }
      jsonObject.put(
          "description", (description != null ? description : "").toString().trim()
      );
    } catch (JSONException e) {
      if (debug) {
        Log.e(TAG, "saveReceipt: " + e);
      }
    }
    if (editReceipt != null) {
      dlHelper.put(
          grocyApi.getObject(ENTITY.RECEIPTS, editReceipt.getId()),
          jsonObject,
          response -> activity.navUtil.navigateUp(),
          this::showErrorMessage
      );
    } else {
      dlHelper.post(
          grocyApi.getObjects(ENTITY.RECEIPTS),
          jsonObject,
          response -> activity.navUtil.navigateUp(),
          this::showErrorMessage
      );
    }
  }

  @Override
  public void deleteObject(int receiptId) {
    dlHelper.delete(
        grocyApi.getObject(ENTITY.RECEIPTS, receiptId),
        response -> activity.navUtil.navigateUp(),
        this::showErrorMessage
    );
  }

  private void showErrorMessage(VolleyError volleyError) {
    activity.showSnackbar(getErrorMessage(volleyError), false);
  }

  private void showDeleteConfirmationDialog() {
    if (editReceipt == null) {
      return;
    }
    new MaterialAlertDialogBuilder(
        activity, R.style.ThemeOverlay_Grocy_AlertDialog_Caution
    ).setTitle(R.string.title_confirmation)
        .setMessage(R.string.msg_receipt_delete)
        .setPositiveButton(R.string.action_delete, (dialog, which) -> {
          performHapticClick();
          deleteObject(editReceipt.getId());
        })
        .setNegativeButton(R.string.action_cancel, (dialog, which) -> performHapticClick())
        .show();
  }

  private void loadReceiptFiles() {
    if (editReceipt == null) {
      return;
    }
    dlHelper.get(
        grocyApi.getObjectsEqualValue(
            ENTITY.RECEIPT_FILES, "receipt_id", String.valueOf(editReceipt.getId())
        ),
        response -> {
          List<ReceiptFile> files = gson.fromJson(
              response, new TypeToken<ArrayList<ReceiptFile>>() {
              }.getType()
          );
          renderReceiptFiles(files);
        },
        this::showErrorMessage
    );
  }

  private void renderReceiptFiles(List<ReceiptFile> files) {
    if (binding == null) {
      return;
    }
    binding.containerFiles.removeAllViews();
    if (files == null) {
      return;
    }
    LayoutInflater inflater = LayoutInflater.from(activity);
    for (ReceiptFile file : files) {
      View row = inflater.inflate(R.layout.row_receipt_file, binding.containerFiles, false);
      TextView name = row.findViewById(R.id.text_file_name);
      ImageView delete = row.findViewById(R.id.button_delete_file);
      name.setText(file.getFileName());
      delete.setOnClickListener(v -> showDeleteFileConfirmationDialog(file));
      binding.containerFiles.addView(row);
    }
  }

  private void uploadFile(Uri uri) {
    if (editReceipt == null) {
      return;
    }
    binding.swipe.setRefreshing(true);
    ExecutorService executor = Executors.newSingleThreadExecutor();
    executor.execute(() -> {
      byte[] bytes = readBytes(uri);
      String extension = getExtension(uri);
      new Handler(Looper.getMainLooper()).post(() -> {
        if (binding == null) {
          executor.shutdown();
          return;
        }
        if (bytes == null) {
          binding.swipe.setRefreshing(false);
          activity.showSnackbar(getString(R.string.error_undefined), false);
        } else {
          String fileName = System.currentTimeMillis() + extension;
          putReceiptFile(fileName, bytes);
        }
        executor.shutdown();
      });
    });
  }

  private void putReceiptFile(String fileName, byte[] bytes) {
    dlHelper.putFile(
        grocyApi.getReceiptFile(fileName),
        bytes,
        () -> linkReceiptFile(fileName),
        error -> {
          binding.swipe.setRefreshing(false);
          showErrorMessage(error);
        }
    );
  }

  private void linkReceiptFile(String fileName) {
    JSONObject jsonObject = new JSONObject();
    try {
      jsonObject.put("receipt_id", editReceipt.getId());
      jsonObject.put("file_name", fileName);
    } catch (JSONException e) {
      if (debug) {
        Log.e(TAG, "linkReceiptFile: " + e);
      }
    }
    dlHelper.post(
        grocyApi.getObjects(ENTITY.RECEIPT_FILES),
        jsonObject,
        response -> {
          binding.swipe.setRefreshing(false);
          loadReceiptFiles();
        },
        error -> {
          binding.swipe.setRefreshing(false);
          showErrorMessage(error);
        }
    );
  }

  private void showDeleteFileConfirmationDialog(ReceiptFile file) {
    new MaterialAlertDialogBuilder(
        activity, R.style.ThemeOverlay_Grocy_AlertDialog_Caution
    ).setTitle(R.string.title_confirmation)
        .setMessage(R.string.msg_receipt_file_delete)
        .setPositiveButton(R.string.action_delete, (dialog, which) -> {
          performHapticClick();
          deleteReceiptFile(file);
        })
        .setNegativeButton(R.string.action_cancel, (dialog, which) -> performHapticClick())
        .show();
  }

  private void deleteReceiptFile(ReceiptFile file) {
    binding.swipe.setRefreshing(true);
    dlHelper.delete(
        grocyApi.getObject(ENTITY.RECEIPT_FILES, file.getId()),
        response -> {
          // Best-effort removal of the stored file; the metadata row is the source of truth
          dlHelper.delete(
              grocyApi.getReceiptFile(file.getFileName()),
              response2 -> {},
              error2 -> {}
          );
          binding.swipe.setRefreshing(false);
          loadReceiptFiles();
        },
        error -> {
          binding.swipe.setRefreshing(false);
          showErrorMessage(error);
        }
    );
  }

  @Nullable
  private byte[] readBytes(Uri uri) {
    try (InputStream inputStream = activity.getContentResolver().openInputStream(uri)) {
      if (inputStream == null) {
        return null;
      }
      ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      byte[] chunk = new byte[8192];
      int read;
      while ((read = inputStream.read(chunk)) != -1) {
        buffer.write(chunk, 0, read);
      }
      return buffer.toByteArray();
    } catch (Exception e) {
      if (debug) {
        Log.e(TAG, "readBytes: " + e);
      }
      return null;
    }
  }

  private String getExtension(Uri uri) {
    String type = activity.getContentResolver().getType(uri);
    String extension = type != null
        ? MimeTypeMap.getSingleton().getExtensionFromMimeType(type)
        : null;
    return extension != null ? "." + extension : "";
  }

  public Toolbar.OnMenuItemClickListener getBottomMenuClickListener() {
    return item -> {
      if (item.getItemId() == R.id.action_delete) {
        showDeleteConfirmationDialog();
        return true;
      }
      return false;
    };
  }

  @NonNull
  @Override
  public String toString() {
    return TAG;
  }
}
