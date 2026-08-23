package androidx.lifecycle;

/**
 * Replaces rikkax {@code rikkax_lifecycle_ViewModel} which calls {@code ViewModel.clear()}.
 * Lifecycle 2.9 exposes {@code clear$lifecycle_viewmodel_release} instead; keeping the old
 * invoke-virtual of {@code clear:()V} is the NoSuchMethodError on app-management back.
 */
public final class rikkax_lifecycle_ViewModel {

    private rikkax_lifecycle_ViewModel() {}

    public static void clear(ViewModel viewModel) {
        viewModel.clear$lifecycle_viewmodel_release();
    }
}
