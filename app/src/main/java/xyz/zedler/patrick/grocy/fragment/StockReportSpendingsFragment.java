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

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import xyz.zedler.patrick.grocy.R;
import xyz.zedler.patrick.grocy.activity.MainActivity;
import xyz.zedler.patrick.grocy.adapter.SpendingItemAdapter;
import xyz.zedler.patrick.grocy.behavior.SystemBarBehavior;
import xyz.zedler.patrick.grocy.databinding.FragmentStockReportSpendingsBinding;
import xyz.zedler.patrick.grocy.helper.InfoFullscreenHelper;
import xyz.zedler.patrick.grocy.model.Event;
import xyz.zedler.patrick.grocy.model.SnackbarMessage;
import xyz.zedler.patrick.grocy.viewmodel.StockReportSpendingsViewModel;
import xyz.zedler.patrick.grocy.viewmodel.StockReportSpendingsViewModel.SpendingItem;

public class StockReportSpendingsFragment extends BaseFragment {

  private static final String TAG = StockReportSpendingsFragment.class.getSimpleName();
  private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

  private MainActivity activity;
  private FragmentStockReportSpendingsBinding binding;
  private StockReportSpendingsViewModel viewModel;
  private InfoFullscreenHelper infoFullscreenHelper;

  @Override
  public View onCreateView(
      @NonNull LayoutInflater inflater,
      ViewGroup container,
      Bundle savedInstanceState
  ) {
    binding = FragmentStockReportSpendingsBinding.inflate(inflater, container, false);
    return binding.getRoot();
  }

  @Override
  public void onDestroyView() {
    super.onDestroyView();
    if (infoFullscreenHelper != null) {
      infoFullscreenHelper.destroyInstance();
      infoFullscreenHelper = null;
    }
    binding = null;
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    activity = (MainActivity) requireActivity();
    viewModel = new ViewModelProvider(this).get(StockReportSpendingsViewModel.class);

    SystemBarBehavior systemBarBehavior = new SystemBarBehavior(activity);
    systemBarBehavior.setAppBar(binding.appBar);
    systemBarBehavior.setScroll(binding.scroll, binding.constraint);
    systemBarBehavior.setUp();
    activity.setSystemBarBehavior(systemBarBehavior);

    binding.toolbar.setNavigationOnClickListener(v -> activity.navUtil.navigateUp());

    infoFullscreenHelper = new InfoFullscreenHelper(binding.frame);
    viewModel.getInfoFullscreenLive().observe(getViewLifecycleOwner(),
        infoFullscreenHelper::setInfo);

    binding.recycler.setLayoutManager(new LinearLayoutManager(requireContext()));

    binding.swipe.setOnRefreshListener(() -> loadWithCurrentSelection());

    viewModel.getIsLoadingLive().observe(getViewLifecycleOwner(),
        loading -> binding.swipe.setRefreshing(loading));

    viewModel.getSpendingItemsLive().observe(getViewLifecycleOwner(), this::updateList);

    viewModel.getTotalSpendLive().observe(getViewLifecycleOwner(),
        total -> binding.textTotal.setText(total));

    viewModel.getEventHandler().observeEvent(getViewLifecycleOwner(), event -> {
      if (event.getType() == Event.SNACKBAR_MESSAGE) {
        activity.showSnackbar(
            ((SnackbarMessage) event).getSnackbar(activity.binding.coordinatorMain));
      }
    });

    binding.chipGroupDate.setOnCheckedStateChangeListener(
        (group, checkedIds) -> loadWithCurrentSelection());

    binding.chipGroupBy.setOnCheckedStateChangeListener((group, checkedIds) -> {
      if (checkedIds.isEmpty()) return;
      int id = checkedIds.get(0);
      if (id == R.id.chip_group_product) {
        viewModel.getGroupByLive().setValue(StockReportSpendingsViewModel.GROUP_BY_PRODUCT);
      } else if (id == R.id.chip_group_product_group) {
        viewModel.getGroupByLive().setValue(StockReportSpendingsViewModel.GROUP_BY_PRODUCT_GROUP);
      } else if (id == R.id.chip_group_store) {
        viewModel.getGroupByLive().setValue(StockReportSpendingsViewModel.GROUP_BY_STORE);
      }
      viewModel.refreshWithGroupBy();
    });

    if (savedInstanceState == null) {
      loadWithCurrentSelection();
    }

    activity.getScrollBehavior().setNestedOverScrollFixEnabled(true);
    activity.getScrollBehavior().setUpScroll(binding.appBar, false, binding.scroll, false);
    activity.getScrollBehavior().setBottomBarVisibility(true);
    activity.updateBottomAppBar(false, R.menu.menu_empty);
  }

  private void loadWithCurrentSelection() {
    int checkedId = binding.chipGroupDate.getCheckedChipId();
    String[] range = getDateRange(checkedId);
    viewModel.loadData(range[0], range[1]);
  }

  private String[] getDateRange(int chipId) {
    LocalDate today = LocalDate.now();
    if (chipId == R.id.chip_last_month) {
      YearMonth lastMonth = YearMonth.now().minusMonths(1);
      return new String[]{
          lastMonth.atDay(1).format(DATE_FMT),
          lastMonth.atEndOfMonth().format(DATE_FMT)
      };
    } else if (chipId == R.id.chip_this_year) {
      return new String[]{
          LocalDate.of(today.getYear(), 1, 1).format(DATE_FMT),
          LocalDate.of(today.getYear(), 12, 31).format(DATE_FMT)
      };
    } else {
      // this month (default)
      YearMonth thisMonth = YearMonth.now();
      return new String[]{
          thisMonth.atDay(1).format(DATE_FMT),
          thisMonth.atEndOfMonth().format(DATE_FMT)
      };
    }
  }

  private void updateList(List<SpendingItem> items) {
    if (items == null || items.isEmpty()) {
      binding.recycler.setAdapter(null);
      return;
    }
    binding.recycler.setAdapter(
        new SpendingItemAdapter(items, amount -> viewModel.formatCurrency(amount))
    );
  }

  @Override
  public void updateConnectivity(boolean online) {
    if (!online == viewModel.isOffline()) return;
    loadWithCurrentSelection();
  }

  @NonNull
  @Override
  public String toString() {
    return TAG;
  }
}
