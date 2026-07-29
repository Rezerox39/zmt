/*
 * repl.c — QuickJS REPL stub for Android
 * Minimal implementation to satisfy CMake build requirements.
 */
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "quickjs-libc.h"

int main(int argc, char **argv) {
    JSRuntime *rt;
    JSContext *ctx;
    rt = JS_NewRuntime();
    ctx = JS_NewContext(rt);
    js_std_add_helpers(ctx, argc, argv);
    js_std_eval_binary(ctx, NULL, 0, 0);
    js_std_loop(ctx);
    JS_FreeContext(ctx);
    JS_FreeRuntime(rt);
    return 0;
}
