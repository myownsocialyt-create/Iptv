import re
import os

print("Starting smali patch...")

# 1. Patch MainAppScreenKt.smali
main_screen_file = "/tmp/smali/classes6/com/example/ui/screens/MainAppScreenKt.smali"
with open(main_screen_file, "r") as f:
    content = f.read()

invoke_split_replacement = """.method public static final InvokeSplitScreen(Ljava/util/List;Ljava/util/List;Lcom/example/data/model/Channel;Lcom/example/player/IptvPlayerManager;ZLkotlin/jvm/functions/Function0;Lkotlin/jvm/functions/Function0;Lkotlin/jvm/functions/Function0;Lkotlin/jvm/functions/Function1;Lkotlin/jvm/functions/Function1;Lcom/example/data/repository/PlaylistSource;Ljava/util/List;Ljava/util/Set;Lkotlin/jvm/functions/Function1;ZLjava/lang/String;Lkotlin/jvm/functions/Function0;ZLjava/lang/String;ZLandroidx/compose/ui/Modifier;Landroidx/compose/runtime/Composer;IIII)V
    .registers 26

    invoke-static/range {p0 .. p25}, Lcom/example/ui/screens/HypnotixSplitScreenKt;->HypnotixSplitScreen(Ljava/util/List;Ljava/util/List;Lcom/example/data/model/Channel;Lcom/example/player/IptvPlayerManager;ZLkotlin/jvm/functions/Function0;Lkotlin/jvm/functions/Function0;Lkotlin/jvm/functions/Function0;Lkotlin/jvm/functions/Function1;Lkotlin/jvm/functions/Function1;Lcom/example/data/repository/PlaylistSource;Ljava/util/List;Ljava/util/Set;Lkotlin/jvm/functions/Function1;ZLjava/lang/String;Lkotlin/jvm/functions/Function0;ZLjava/lang/String;ZLandroidx/compose/ui/Modifier;Landroidx/compose/runtime/Composer;IIII)V

    return-void
.end method"""

start_marker = ".method public static final InvokeSplitScreen("
end_marker = ".end method"

if start_marker in content:
    idx_start = content.index(start_marker)
    idx_end = content.find(end_marker, idx_start) + len(end_marker)
    new_content = content[:idx_start] + invoke_split_replacement + content[idx_end:]
    with open(main_screen_file, "w") as f:
        f.write(new_content)
    print("MainAppScreenKt.smali patched successfully")
else:
    print("Warning: InvokeSplitScreen start_marker not found in MainAppScreenKt.smali")

# 2. Patch item key lambdas
key_files = [
    "/tmp/smali/classes5/com/example/player/IptvVideoPlayerKt$IptvVideoPlayer$9$13$1$invoke$lambda$8$lambda$7$lambda$6$$inlined$items$default$2.smali",
    "/tmp/smali/classes6/com/example/ui/screens/HypnotixSplitScreenKt$HypnotixSplitScreen$7$invoke$lambda$22$lambda$21$lambda$12$lambda$11$$inlined$items$default$2.smali",
    "/tmp/smali/classes6/com/example/ui/screens/HypnotixSplitScreenKt$HypnotixSplitScreen$7$invoke$lambda$22$lambda$21$lambda$20$lambda$19$$inlined$items$default$2.smali",
    "/tmp/smali/classes6/com/example/ui/screens/HypnotixSplitScreenKt$HypnotixSplitScreen$7$invoke$lambda$49$lambda$46$lambda$38$lambda$37$$inlined$items$default$2.smali",
    "/tmp/smali/classes6/com/example/ui/screens/HypnotixSplitScreenKt$HypnotixSplitScreen$7$invoke$lambda$49$lambda$46$lambda$45$lambda$44$$inlined$items$default$2.smali"
]

method_replacement = """.method public final invoke(I)Ljava/lang/Object;
    .registers 3
    .param p1, "index"    # I

    invoke-static {p1}, Ljava/lang/Integer;->valueOf(I)Ljava/lang/Integer;

    move-result-object v0

    return-object v0
.end method"""

for kf in key_files:
    if not os.path.exists(kf):
        print("Not found:", kf)
        continue
    with open(kf, "r") as f:
        kf_content = f.read()
    
    start_m = ".method public final invoke(I)Ljava/lang/Object;"
    if start_m in kf_content:
        idx_s = kf_content.index(start_m)
        idx_e = kf_content.find(".end method", idx_s) + len(".end method")
        kf_new = kf_content[:idx_s] + method_replacement + kf_content[idx_e:]
        with open(kf, "w") as f:
            f.write(kf_new)
        print("Patched key in", os.path.basename(kf))
    else:
        print("Warning: start_m not found in", os.path.basename(kf))

# 3. Patch IptvViewModel.smali
vm_file = "/tmp/smali/classes10/com/example/ui/viewmodel/IptvViewModel.smali"
with open(vm_file, "r") as f:
    vm_content = f.read()

# Replace disclaimer check in playChannel: if-nez v2, :cond_8c -> goto :cond_8c
vm_new = vm_content.replace("if-nez v2, :cond_8c", "goto :cond_8c")

# Also ensure default of _hasAcceptedMediaDisclaimer is true
vm_new = re.sub(
    r"(\.line 105\s*\n\s*)invoke-static \{v2\}, Lkotlinx/coroutines/flow/StateFlowKt;->MutableStateFlow",
    r"const/4 v2, 0x1\n    invoke-static {v2}, Ljava/lang/Boolean;->valueOf(Z)Ljava/lang/Boolean;\n    move-result-object v2\n    \g<1>invoke-static {v2}, Lkotlinx/coroutines/flow/StateFlowKt;->MutableStateFlow",
    vm_new
)

if vm_new != vm_content:
    with open(vm_file, "w") as f:
        f.write(vm_new)
    print("IptvViewModel.smali patched successfully")
else:
    print("Warning: IptvViewModel.smali pattern not matched")

print("All smali patches completed.")
