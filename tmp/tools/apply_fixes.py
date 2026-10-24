import os
import re

print("Applying IPTV playback crash fixes...")

# -------------------------------------------------------------
# 1. Fix AspectRatioMode in classes5/com/example/player/IptvVideoPlayerKt.smali
# -------------------------------------------------------------
p1 = "/tmp/smali/classes5/com/example/player/IptvVideoPlayerKt.smali"
if os.path.exists(p1):
    with open(p1, "r") as f:
        content = f.read()

    # Replace NoWhenBranchMatchedException in update lambda
    target_err = """    packed-switch v1, :pswitch_data_3a

    new-instance v1, Lkotlin/NoWhenBranchMatchedException;

    invoke-direct {v1}, Lkotlin/NoWhenBranchMatchedException;-><init>()V

    throw v1"""

    replacement_err = """    packed-switch v1, :pswitch_data_3a

    const/4 v2, 0x0

    goto :goto_34"""

    target_switch = """:pswitch_data_3a
    .packed-switch 0x1
        :pswitch_33
        :pswitch_32
        :pswitch_30
        :pswitch_30
    .end packed-switch"""

    replacement_switch = """:pswitch_data_3a
    .packed-switch 0x1
        :pswitch_33
        :pswitch_32
        :pswitch_30
        :pswitch_30
        :pswitch_2f
        :pswitch_33
    .end packed-switch"""

    if target_err in content:
        content = content.replace(target_err, replacement_err)
        print("Patched NoWhenBranchMatchedException in IptvVideoPlayerKt.smali")
    else:
        print("WARNING: target_err not found in IptvVideoPlayerKt.smali")

    if target_switch in content:
        content = content.replace(target_switch, replacement_switch)
        print("Patched pswitch_data_3a in IptvVideoPlayerKt.smali")
    else:
        print("WARNING: target_switch not found in IptvVideoPlayerKt.smali")

    with open(p1, "w") as f:
        f.write(content)
else:
    print(f"Error: {p1} not found")

# -------------------------------------------------------------
# 1b. Fix AspectRatioMode in classes5/com/example/player/IptvVideoPlayerKt$IptvVideoPlayer$9$5$1.smali
# -------------------------------------------------------------
p1b = "/tmp/smali/classes5/com/example/player/IptvVideoPlayerKt$IptvVideoPlayer$9$5$1.smali"
if os.path.exists(p1b):
    with open(p1b, "r") as f:
        content1b = f.read()

    target_err_1b = """    packed-switch v1, :pswitch_data_bc

    new-instance v1, Lkotlin/NoWhenBranchMatchedException;

    invoke-direct {v1}, Lkotlin/NoWhenBranchMatchedException;-><init>()V

    throw v1"""

    repl_1b = """    packed-switch v1, :pswitch_data_bc

    sget-object v1, Lcom/example/player/AspectRatioMode;->FIT_16_9:Lcom/example/player/AspectRatioMode;

    goto :goto_8f"""

    target_err_1c = """    packed-switch v0, :pswitch_data_cc

    new-instance v0, Lkotlin/NoWhenBranchMatchedException;

    invoke-direct {v0}, Lkotlin/NoWhenBranchMatchedException;-><init>()V

    throw v0"""

    repl_1c = """    packed-switch v0, :pswitch_data_cc

    sget-object v0, Lcom/example/player/AspectRatioMode;->FIT_16_9:Lcom/example/player/AspectRatioMode;

    goto :goto_b6"""

    if target_err_1b in content1b:
        content1b = content1b.replace(target_err_1b, repl_1b)
        print("Patched target_err_1b in IptvVideoPlayer$9$5$1.smali")
    if target_err_1c in content1b:
        content1b = content1b.replace(target_err_1c, repl_1c)
        print("Patched target_err_1c in IptvVideoPlayer$9$5$1.smali")

    with open(p1b, "w") as f:
        f.write(content1b)

# -------------------------------------------------------------
# 2. Fix InStreamAdManager in classes8/com/example/ad/InStreamAdManager.smali
# -------------------------------------------------------------
p2 = "/tmp/smali/classes8/com/example/ad/InStreamAdManager.smali"
if os.path.exists(p2):
    with open(p2, "r") as f:
        content2 = f.read()

    # Replace onChannelSwitched method
    start_m = ".method public final onChannelSwitched(Landroid/content/Context;Ljava/lang/String;Lkotlin/jvm/functions/Function0;Lkotlin/jvm/functions/Function0;)V"
    idx_s = content2.find(start_m)
    if idx_s != -1:
        idx_e = content2.find(".end method", idx_s) + len(".end method")
        new_onChannelSwitched = """.method public final onChannelSwitched(Landroid/content/Context;Ljava/lang/String;Lkotlin/jvm/functions/Function0;Lkotlin/jvm/functions/Function0;)V
    .registers 6
    .param p1, "context"    # Landroid/content/Context;
    .param p2, "streamUrl"    # Ljava/lang/String;
    .param p3, "onStartAd"    # Lkotlin/jvm/functions/Function0;
    .param p4, "onEndAd"    # Lkotlin/jvm/functions/Function0;

    if-eqz p4, :cond_5

    invoke-interface {p4}, Lkotlin/jvm/functions/Function0;->invoke()Ljava/lang/Object;

    :cond_5
    return-void
.end method"""
        content2 = content2[:idx_s] + new_onChannelSwitched + content2[idx_e:]
        print("Patched onChannelSwitched in InStreamAdManager.smali")
    else:
        print("WARNING: onChannelSwitched not found in InStreamAdManager.smali")

    # Replace schedulePeriodicVideoAd
    start_sp = ".method public final schedulePeriodicVideoAd(Landroid/content/Context;)V"
    idx_sp = content2.find(start_sp)
    if idx_sp != -1:
        idx_spe = content2.find(".end method", idx_sp) + len(".end method")
        new_sp = """.method public final schedulePeriodicVideoAd(Landroid/content/Context;)V
    .registers 2
    .param p1, "context"    # Landroid/content/Context;

    return-void
.end method"""
        content2 = content2[:idx_sp] + new_sp + content2[idx_spe:]
        print("Patched schedulePeriodicVideoAd in InStreamAdManager.smali")

    # Replace scheduleMidRollAd
    start_mr = ".method public final scheduleMidRollAd(Landroid/content/Context;JLkotlin/jvm/functions/Function0;Lkotlin/jvm/functions/Function0;)V"
    idx_mr = content2.find(start_mr)
    if idx_mr != -1:
        idx_mre = content2.find(".end method", idx_mr) + len(".end method")
        new_mr = """.method public final scheduleMidRollAd(Landroid/content/Context;JLkotlin/jvm/functions/Function0;Lkotlin/jvm/functions/Function0;)V
    .registers 6

    return-void
.end method"""
        content2 = content2[:idx_mr] + new_mr + content2[idx_mre:]
        print("Patched scheduleMidRollAd in InStreamAdManager.smali")

    # Replace preloadNative
    start_pn = ".method public final preloadNative(Landroid/content/Context;)V"
    idx_pn = content2.find(start_pn)
    if idx_pn != -1:
        idx_pne = content2.find(".end method", idx_pn) + len(".end method")
        new_pn = """.method public final preloadNative(Landroid/content/Context;)V
    .registers 2
    .param p1, "context"    # Landroid/content/Context;

    return-void
.end method"""
        content2 = content2[:idx_pn] + new_pn + content2[idx_pne:]
        print("Patched preloadNative in InStreamAdManager.smali")

    # Replace preloadInterstitial
    start_pi = ".method private final preloadInterstitial(Landroid/content/Context;)V"
    idx_pi = content2.find(start_pi)
    if idx_pi != -1:
        idx_pie = content2.find(".end method", idx_pi) + len(".end method")
        new_pi = """.method private final preloadInterstitial(Landroid/content/Context;)V
    .registers 2
    .param p1, "context"    # Landroid/content/Context;

    return-void
.end method"""
        content2 = content2[:idx_pi] + new_pi + content2[idx_pie:]
        print("Patched preloadInterstitial in InStreamAdManager.smali")

    with open(p2, "w") as f:
        f.write(content2)

# -------------------------------------------------------------
# 3. Fix MainAppScreenKt.smali Modifier before InvokeSplitScreen
# -------------------------------------------------------------
p3 = "/tmp/smali/classes6/com/example/ui/screens/MainAppScreenKt.smali"
if os.path.exists(p3):
    with open(p3, "r") as f:
        content3 = f.read()

    target_inv = """    sput-object v46, Lcom/example/ui/screens/MainAppScreenKt;->sXtreamAccountsState:Landroidx/compose/runtime/State;

    sput-object v47, Lcom/example/ui/screens/MainAppScreenKt;->sActiveXtreamAccountState:Landroidx/compose/runtime/State;

    sput-object v48, Lcom/example/ui/screens/MainAppScreenKt;->sXtreamChannelsState:Landroidx/compose/runtime/State;

    sput-object v49, Lcom/example/ui/screens/MainAppScreenKt;->sXtreamVodChannelsState:Landroidx/compose/runtime/State;

    sput-object v50, Lcom/example/ui/screens/MainAppScreenKt;->sIsXtreamLoadingState:Landroidx/compose/runtime/State;

    invoke-static/range {v16 .. v41}, Lcom/example/ui/screens/MainAppScreenKt;->InvokeSplitScreen"""

    repl_inv = """    sput-object v46, Lcom/example/ui/screens/MainAppScreenKt;->sXtreamAccountsState:Landroidx/compose/runtime/State;

    sput-object v47, Lcom/example/ui/screens/MainAppScreenKt;->sActiveXtreamAccountState:Landroidx/compose/runtime/State;

    sput-object v48, Lcom/example/ui/screens/MainAppScreenKt;->sXtreamChannelsState:Landroidx/compose/runtime/State;

    sput-object v49, Lcom/example/ui/screens/MainAppScreenKt;->sXtreamVodChannelsState:Landroidx/compose/runtime/State;

    sput-object v50, Lcom/example/ui/screens/MainAppScreenKt;->sIsXtreamLoadingState:Landroidx/compose/runtime/State;

    sget-object v36, Landroidx/compose/ui/Modifier;->Companion:Landroidx/compose/ui/Modifier$Companion;

    invoke-static/range {v16 .. v41}, Lcom/example/ui/screens/MainAppScreenKt;->InvokeSplitScreen"""

    if target_inv in content3:
        content3 = content3.replace(target_inv, repl_inv)
        print("Patched v36 modifier in MainAppScreenKt.smali")
    else:
        print("WARNING: target_inv not found in MainAppScreenKt.smali")

    with open(p3, "w") as f:
        f.write(content3)

# -------------------------------------------------------------
# 4. Fix IptvViewModel$playChannel$1.smali recordRecent
# -------------------------------------------------------------
p4 = "/tmp/smali/classes10/com/example/ui/viewmodel/IptvViewModel$playChannel$1.smali"
if os.path.exists(p4):
    with open(p4, "r") as f:
        content4 = f.read()

    new_invokeSuspend = """.method public final invokeSuspend(Ljava/lang/Object;)Ljava/lang/Object;
    .registers 7

    invoke-static {}, Lkotlin/coroutines/intrinsics/IntrinsicsKt;->getCOROUTINE_SUSPENDED()Ljava/lang/Object;

    move-result-object v0

    iget v1, p0, Lcom/example/ui/viewmodel/IptvViewModel$playChannel$1;->label:I

    packed-switch v1, :pswitch_data_30

    new-instance p1, Ljava/lang/IllegalStateException;

    const-string v0, "call to 'resume' before 'invoke' with coroutine"

    invoke-direct {p1, v0}, Ljava/lang/IllegalStateException;-><init>(Ljava/lang/String;)V

    throw p1

    .local p1, "$result":Ljava/lang/Object;
    :pswitch_11
    :try_start_11
    invoke-static {p1}, Lkotlin/ResultKt;->throwOnFailure(Ljava/lang/Object;)V
    :try_end_14
    .catchall {:try_start_11 .. :try_end_14} :catch_exit

    goto :goto_2d

    .end local p1    # "$result":Ljava/lang/Object;
    :pswitch_15
    invoke-static {p1}, Lkotlin/ResultKt;->throwOnFailure(Ljava/lang/Object;)V

    .line 458
    .restart local p1    # "$result":Ljava/lang/Object;
    iget-object v1, p0, Lcom/example/ui/viewmodel/IptvViewModel$playChannel$1;->this$0:Lcom/example/ui/viewmodel/IptvViewModel;

    invoke-static {v1}, Lcom/example/ui/viewmodel/IptvViewModel;->access$getRepository$p(Lcom/example/ui/viewmodel/IptvViewModel;)Lcom/example/data/repository/PlaylistRepository;

    move-result-object v1

    iget-object v2, p0, Lcom/example/ui/viewmodel/IptvViewModel$playChannel$1;->$channelWithFav:Lcom/example/data/model/Channel;

    move-object v3, p0

    check-cast v3, Lkotlin/coroutines/Continuation;

    const/4 v4, 0x1

    iput v4, p0, Lcom/example/ui/viewmodel/IptvViewModel$playChannel$1;->label:I

    :try_start_28
    invoke-virtual {v1, v2, v3}, Lcom/example/data/repository/PlaylistRepository;->recordRecent(Lcom/example/data/model/Channel;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;

    move-result-object v1
    :try_end_2c
    .catchall {:try_start_28 .. :try_end_2c} :catch_exit

    if-ne v1, v0, :cond_2d

    return-object v0

    :cond_2d
    :goto_2d
    :catch_exit
    sget-object v0, Lkotlin/Unit;->INSTANCE:Lkotlin/Unit;

    return-object v0

    :pswitch_data_30
    .packed-switch 0x0
        :pswitch_15
        :pswitch_11
    .end packed-switch
.end method"""

    idx_s4 = content4.find(".method public final invokeSuspend(Ljava/lang/Object;)Ljava/lang/Object;")
    if idx_s4 != -1:
        idx_e4 = content4.find(".end method", idx_s4) + len(".end method")
        content4 = content4[:idx_s4] + new_invokeSuspend + content4[idx_e4:]
        print("Patched invokeSuspend with safe try-catch in IptvViewModel$playChannel$1.smali")
    else:
        print("WARNING: invokeSuspend not found in IptvViewModel$playChannel$1.smali")

    with open(p4, "w") as f:
        f.write(content4)

print("All crash fix patches applied successfully.")
