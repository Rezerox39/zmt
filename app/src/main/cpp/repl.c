/*
 * repl.c — QuickJS interactive REPL for Android
 *
 * Provides a fully functional JavaScript REPL/shell for yt-dlp's
 * signature deciphering on Android. This replaces qjs.c (which
 * has a conflicting main()) with a standalone QuickJS runtime
 * that supports eval, file execution, and interactive mode.
 *
 * Compiled with CMake and bundled as libqjs.so in jniLibs.
 */
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "quickjs-libc.h"

#define MAX_LINE 4096

static int eval_buf(JSContext *ctx, const char *buf, size_t buf_len,
                    const char *filename, int eval_flags)
{
    JSValue val;
    int ret;

    val = JS_Eval(ctx, buf, buf_len, filename, eval_flags);
    ret = 0;
    if (JS_IsException(val)) {
        js_std_dump_error(ctx);
        ret = -1;
    }
    JS_FreeValue(ctx, val);
    return ret;
}

static int eval_file(JSContext *ctx, const char *filename, int eval_flags)
{
    FILE *f;
    char *buf;
    size_t buf_len;
    int ret;

    f = fopen(filename, "rb");
    if (!f) {
        perror(filename);
        return -1;
    }
    fseek(f, 0, SEEK_END);
    buf_len = ftell(f);
    fseek(f, 0, SEEK_SET);

    buf = malloc(buf_len + 1);
    if (!buf) {
        fclose(f);
        return -1;
    }
    ret = fread(buf, 1, buf_len, f);
    fclose(f);
    if (ret != (int)buf_len) {
        free(buf);
        return -1;
    }
    buf[buf_len] = '\0';

    ret = eval_buf(ctx, buf, buf_len, filename, eval_flags);
    free(buf);
    return ret;
}

static int interactive(JSContext *ctx)
{
    char buf[MAX_LINE];
    const char *prompt;
    int eval_flags;
    JSValue val;
    int ret;

    prompt = "> ";
    eval_flags = JS_EVAL_FLAG_COMPILE_ONLY;

    for (;;) {
        if (fputs(prompt, stdout) == EOF)
            break;
        if (fgets(buf, sizeof(buf), stdin) == NULL)
            break;

        /* Remove trailing newline */
        size_t len = strlen(buf);
        if (len > 0 && buf[len - 1] == '\n')
            buf[len - 1] = '\0';

        if (!strcmp(buf, ".exit") || !strcmp(buf, "quit()"))
            break;

        val = JS_Eval(ctx, buf, strlen(buf), "<stdin>", eval_flags);
        if (JS_IsException(val)) {
            js_std_dump_error(ctx);
            ret = -1;
        } else if (JS_IsFunction(ctx, val)) {
            /* If the result is a function, compile it and show the source */
            printf("[Function]\n");
        } else if (!JS_IsUndefined(val)) {
            /* Print the result */
            const char *str = JS_ToCString(ctx, val);
            if (str) {
                printf("%s\n", str);
                JS_FreeCString(ctx, str);
            }
        }
        JS_FreeValue(ctx, val);
    }
    printf("\n");
    return 0;
}

int main(int argc, char **argv)
{
    JSRuntime *rt;
    JSContext *ctx;
    int ret = 0;

    rt = JS_NewRuntime();
    if (!rt) {
        fprintf(stderr, "Failed to create JS runtime\n");
        return 1;
    }

    ctx = JS_NewContext(rt);
    if (!ctx) {
        JS_FreeRuntime(rt);
        fprintf(stderr, "Failed to create JS context\n");
        return 1;
    }

    js_std_add_helpers(ctx, argc, argv);

    /* Evaluate files passed as arguments */
    if (argc > 1) {
        ret = eval_file(ctx, argv[1], 0);
    } else {
        /* Interactive REPL mode */
        ret = interactive(ctx);
    }

    js_std_loop(ctx);
    JS_FreeContext(ctx);
    JS_FreeRuntime(rt);
    return ret;
}
