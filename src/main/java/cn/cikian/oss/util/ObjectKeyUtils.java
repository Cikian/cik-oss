package cn.cikian.oss.util;

/**
 * objectKey 归一化工具
 * <p>
 * 调用方（尤其是用 {@code File.separator} 手工拼接路径的业务代码）很容易把
 * {@code "dir" + File.separator + "/biz/path"} 拼成 {@code dir\/biz/path}，
 * 简单地把反斜杠替换成正斜杠就会产生 {@code dir//biz/path} 这种双斜杠，
 * 而 {@code a//b} 与 {@code a/b} 在对象存储里是两个不同的 key，
 * 会导致 URL、对象键、删除/列举路径不一致。
 * <p>
 * 这里统一做四件事，保证无论调用方怎么传，最终 key 都是规范形式：
 * <ol>
 *     <li>反斜杠 {@code \} 统一转成正斜杠 {@code /}</li>
 *     <li>空格转下划线（历史行为，保持兼容）</li>
 *     <li>折叠重复斜杠，{@code /{2,}} 归一为单个 {@code /}</li>
 *     <li>去掉首尾斜杠，保证与 {@code buildObjectUrl} 生成的 URL 路径一致</li>
 * </ol>
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-11
 */
public final class ObjectKeyUtils {

    private ObjectKeyUtils() {
    }

    /**
     * 归一化 objectKey
     *
     * @param objectKey 原始对象键，允许为 null
     * @return 归一化后的对象键；入参为 null 时返回 null
     */
    public static String normalize(String objectKey) {
        if (objectKey == null) {
            return null;
        }

        // 1. 反斜杠转正斜杠 + 空格转下划线（保持原有行为）
        String key = objectKey.replace("\\", "/").replace(" ", "_");

        // 2. 折叠重复斜杠：dir//biz/path -> dir/biz/path
        key = key.replaceAll("/{2,}", "/");

        // 3. 去掉首尾斜杠：/dir/path/ -> dir/path
        while (key.startsWith("/")) {
            key = key.substring(1);
        }
        while (key.endsWith("/")) {
            key = key.substring(0, key.length() - 1);
        }

        return key;
    }
}
