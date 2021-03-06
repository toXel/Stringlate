package io.github.lonamiwebs.stringlate.utilities;

public class Constants {
    public final static String EXTRA_REPO = "io.github.lonamiwebs.stringlate.REPO";
    public final static String EXTRA_LOCALE = "io.github.lonamiwebs.stringlate.LOCALE";

    public final static String EXTRA_REMOTE_PATH = "io.github.lonamiwebs.stringlate.REMOTE_PATH";
    public final static String EXTRA_DESCRIPTION = "io.github.lonamiwebs.stringlate.DESCRIPTION";

    // Default app limit to show when discovering new applications
    public final static int DEFAULT_APPS_LIMIT = 50;

    // Arbitrary numbers were chosen
    public final static int RESULT_REPO_DISCOVERED = 707;
    public final static int RESULT_CREATE_FILE = 708;
    public final static int RESULT_STRING_SELECTED = 709;
    public final static int RESULT_OPEN_TREE = 710;

    // Online help
    public final static String ONLINE_HELP_DEFAULT_LOCALE = "en";
    public final static String[] ONLINE_HELP_LOCALES = { "en", "es" };
    public final static String ONLINE_HELP_INDEX =
            "https://github.com/LonamiWebs/Stringlate/blob/master/help/index.md";

    // GitHub OAuth (https://developer.github.com/v3/oauth/#scopes)
    // Scopes joined by '%20', although result scopes are joined by ','
    public final static String GITHUB_AUTH_URL = "https://github.com/login/oauth/authorize?scope=%s&client_id=%s";
    public final static String GITHUB_COMPLETE_AUTH_URL = "https://github.com/login/oauth/access_token";
    public final static String[] GITHUB_WANTED_SCOPES = { "public_repo", "gist" };
    public final static String GITHUB_CLIENT_ID = "994d17302a9e34077cd9";
    public final static String GITHUB_CLIENT_SECRET = "863d91a38332b3648cd951c0c498fd2520a8dd9d";
    // Not very secret anymore is it ^ ? Some discussion available at:
    // http://stackoverflow.com/q/4057277 and http://stackoverflow.com/q/4419915

    // Material colors (700): https://material.io/guidelines/style/color.html
    public final static int[] MATERIAL_COLORS = {
            0xFFD32F2F, 0xFFC2185B, 0xFF7B1FA2, 0xFF512DA8, 0xFF303F9F, 0xFF1976D2,
            0xFF0288D1, 0xFF0097A7, 0xFF00796B, 0xFF388E3C, 0xFF689F38, 0xFFAFB42B,
            0xFFFBC02D, 0xFFFFA000, 0xFFF57C00, 0xFFE64A19, 0xFF5D4037, 0xFF616161
    };
}
