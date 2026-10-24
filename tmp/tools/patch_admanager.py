import os
import re

print("Patching AdManager.smali...")

p = "/tmp/smali/classes8/com/example/ad/AdManager.smali"
if not os.path.exists(p):
    print(f"Error: {p} not found")
    exit(1)

with open(p, "r") as f:
    content = f.read()

def replace_method(text, method_sig, new_body):
    idx_s = text.find(method_sig)
    if idx_s == -1:
        print(f"WARNING: {method_sig} not found")
        return text
    idx_e = text.find(".end method", idx_s) + len(".end method")
    print(f"Replaced {method_sig[:50]}...")
    return text[:idx_s] + new_body + text[idx_e:]

content = replace_method(
    content,
    ".method public final initialize(Landroid/app/Application;)V",
    """.method public final initialize(Landroid/app/Application;)V
    .registers 2
    return-void
.end method"""
)

content = replace_method(
    content,
    ".method private final attachBackButtonBlockerIfNeeded(Landroid/app/Activity;)V",
    """.method private final attachBackButtonBlockerIfNeeded(Landroid/app/Activity;)V
    .registers 2
    return-void
.end method"""
)

content = replace_method(
    content,
    ".method public final onUserAction()V",
    """.method public final onUserAction()V
    .registers 1
    return-void
.end method"""
)

content = replace_method(
    content,
    ".method public final onUserNavigated(Landroid/app/Activity;)V",
    """.method public final onUserNavigated(Landroid/app/Activity;)V
    .registers 2
    return-void
.end method"""
)

content = replace_method(
    content,
    ".method public final onPlaylistOrAccountAdded()V",
    """.method public final onPlaylistOrAccountAdded()V
    .registers 1
    return-void
.end method"""
)

content = replace_method(
    content,
    ".method public final onActivityStartedHook(Landroid/app/Activity;)V",
    """.method public final onActivityStartedHook(Landroid/app/Activity;)V
    .registers 2
    return-void
.end method"""
)

content = replace_method(
    content,
    ".method public final onActivityStoppedHook(Landroid/app/Activity;)V",
    """.method public final onActivityStoppedHook(Landroid/app/Activity;)V
    .registers 2
    return-void
.end method"""
)

content = replace_method(
    content,
    ".method public final preloadOpenAd(Landroid/content/Context;)V",
    """.method public final preloadOpenAd(Landroid/content/Context;)V
    .registers 2
    return-void
.end method"""
)

content = replace_method(
    content,
    ".method public final preloadExitAd(Landroid/content/Context;)V",
    """.method public final preloadExitAd(Landroid/content/Context;)V
    .registers 2
    return-void
.end method"""
)

content = replace_method(
    content,
    ".method public final showAppOpenAdIfEligible(Landroid/app/Activity;Lkotlin/jvm/functions/Function0;)V",
    """.method public final showAppOpenAdIfEligible(Landroid/app/Activity;Lkotlin/jvm/functions/Function0;)V
    .registers 3
    if-eqz p2, :cond_5
    invoke-interface {p2}, Lkotlin/jvm/functions/Function0;->invoke()Ljava/lang/Object;
    :cond_5
    return-void
.end method"""
)

content = replace_method(
    content,
    ".method public final showExitInterstitial(Landroid/app/Activity;Lkotlin/jvm/functions/Function0;)V",
    """.method public final showExitInterstitial(Landroid/app/Activity;Lkotlin/jvm/functions/Function0;)V
    .registers 3
    if-eqz p2, :cond_5
    invoke-interface {p2}, Lkotlin/jvm/functions/Function0;->invoke()Ljava/lang/Object;
    :cond_5
    return-void
.end method"""
)

with open(p, "w") as f:
    f.write(content)

print("AdManager.smali patched successfully.")
