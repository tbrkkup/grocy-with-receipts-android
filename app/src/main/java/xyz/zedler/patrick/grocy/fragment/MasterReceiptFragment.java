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
import android.os.Bundle;
import android.text.Editable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.preference.PreferenceManager;
import com.android.volley.VolleyError;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.util.ArrayList;
import java.util.List;
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

    fillForm();

    if (savedInstanceState == null) {
      download();
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
