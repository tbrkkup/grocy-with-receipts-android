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

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.github.mikephil.charting.animation.Easing;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.formatter.PercentFormatter;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import xyz.zedler.patrick.grocy.R;
import xyz.zedler.patrick.grocy.activity.MainActivity;
import xyz.zedler.patrick.grocy.adapter.SpendingItemAdapter;
import xyz.zedler.patrick.grocy.behavior.SystemBarBehavior;
import xyz.zedler.patrick.grocy.databinding.FragmentStockReportSpendingsBinding;
import xyz.zedler.patrick.grocy.helper.InfoFullscreenHelper;
import xyz.zedler.patrick.grocy.model.Event;
import xyz.zedler.patrick.grocy.model.SnackbarMessage;
import xyz.zedler.patrick.grocy.util.ResUtil;
import xyz.zedler.patrick.grocy.viewmodel.StockReportSpendingsViewModel;
import xyz.zedler.patrick.grocy.viewmodel.StockReportSpendingsViewModel.SpendingItem;

public class StockReportSpendingsFragment extends BaseFragment {

  private static final String TAG = StockReportSpendingsFragment.class.getSimpleName();
  private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

  // Material 3 compatible palette — works on both light and dark backgrounds
  private static final int[] CHART_COLORS = {
      0xFF6750A4, // M3 primary purple
      0xFF4CAF50, // green
      0xFF2196F3, // blue
      0xFFF44336, // red
      0xFFFF9800, // orange
      0xFF00BCD4, // cyan
      0xFFE91E63, // pink
      0xFF9C27B0, // deep purple
      0xFF8BC34A, // light green
      0xFF03A9F4, // light blue
      0xFFFFEB3B, // yellow
      0xFF795548, // brown
  };

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

    binding.swipe.setOnRefreshListener(this::loadWithCurrentSelection);

    viewModel.getIsLoadingLive().observe(getViewLifecycleOwner(),
        loading -> binding.swipe.setRefreshing(loading));

    viewModel.getSpendingItemsLive().observe(getViewLifecycleOwner(), this::updateChart);

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

    setupChart();

    if (savedInstanceState == null) {
      loadWithCurrentSelection();
    }

    activity.getScrollBehavior().setNestedOverScrollFixEnabled(true);
    activity.getScrollBehavior().setUpScroll(binding.appBar, false, binding.scroll, false);
    activity.getScrollBehavior().setBottomBarVisibility(true);
    activity.updateBottomAppBar(false, R.menu.menu_empty);
  }

  private void setupChart() {
    int colorOnSurface = ResUtil.getColor(requireContext(), R.attr.colorOnSurface);
    int colorSurface = ResUtil.getColor(requireContext(), R.attr.colorSurface);

    binding.pieChart.setUsePercentValues(true);
    binding.pieChart.getDescription().setEnabled(false);
    binding.pieChart.setDrawHoleEnabled(true);
    binding.pieChart.setHoleColor(colorSurface);
    binding.pieChart.setHoleRadius(52f);
    binding.pieChart.setTransparentCircleRadius(57f);
    binding.pieChart.setTransparentCircleColor(colorSurface);
    binding.pieChart.setTransparentCircleAlpha(110);
    binding.pieChart.setDrawCenterText(false);
    binding.pieChart.setRotationEnabled(true);
    binding.pieChart.setHighlightPerTapEnabled(true);
    binding.pieChart.setDrawEntryLabels(false);

    // Legend
    com.github.mikephil.charting.components.Legend legend = binding.pieChart.getLegend();
    legend.setEnabled(true);
    legend.setTextColor(colorOnSurface);
    legend.setTextSize(12f);
    legend.setForm(com.github.mikephil.charting.components.Legend.LegendForm.CIRCLE);
    legend.setFormSize(10f);
    legend.setWordWrapEnabled(true);
    legend.setHorizontalAlignment(
        com.github.mikephil.charting.components.Legend.LegendHorizontalAlignment.CENTER);
    legend.setVerticalAlignment(
        com.github.mikephil.charting.components.Legend.LegendVerticalAlignment.BOTTOM);
    legend.setOrientation(
        com.github.mikephil.charting.components.Legend.LegendOrientation.HORIZONTAL);
    legend.setDrawInside(false);

    binding.pieChart.setNoDataText(getString(R.string.error_empty_stock));
    binding.pieChart.setNoDataTextColor(colorOnSurface);
  }

  private void updateChart(List<SpendingItem> items) {
    if (items == null || items.isEmpty()) {
      binding.pieChart.clear();
      binding.pieChart.invalidate();
      binding.recycler.setAdapter(null);
      return;
    }

    // Show only top N slices, group the rest as "Other"
    int maxSlices = 8;
    List<SpendingItem> chartItems = items.size() > maxSlices
        ? items.subList(0, maxSlices) : items;
    double otherTotal = 0;
    if (items.size() > maxSlices) {
      for (int i = maxSlices; i < items.size(); i++) {
        otherTotal += items.get(i).total;
      }
    }

    List<PieEntry> entries = new ArrayList<>();
    for (SpendingItem item : chartItems) {
      entries.add(new PieEntry((float) item.total, item.name));
    }
    if (otherTotal > 0) {
      entries.add(new PieEntry((float) otherTotal, getString(R.string.subtitle_others)));
    }

    PieDataSet dataSet = new PieDataSet(entries, "");
    dataSet.setColors(CHART_COLORS);
    dataSet.setSliceSpace(2f);
    dataSet.setSelectionShift(6f);
    dataSet.setValueLinePart1OffsetPercentage(80f);
    dataSet.setValueLinePart1Length(0.3f);
    dataSet.setValueLinePart2Length(0.4f);
    dataSet.setValueTextColor(Color.WHITE);
    dataSet.setValueTextSize(11f);

    PieData pieData = new PieData(dataSet);
    pieData.setValueFormatter(new PercentFormatter(binding.pieChart));
    pieData.setValueTextSize(11f);
    pieData.setValueTextColor(Color.WHITE);

    binding.pieChart.setData(pieData);
    binding.pieChart.animateY(600, Easing.EaseInOutQuad);
    binding.pieChart.invalidate();

    binding.recycler.setAdapter(
        new SpendingItemAdapter(items, amount -> viewModel.formatCurrency(amount))
    );
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
      YearMonth thisMonth = YearMonth.now();
      return new String[]{
          thisMonth.atDay(1).format(DATE_FMT),
          thisMonth.atEndOfMonth().format(DATE_FMT)
      };
    }
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
