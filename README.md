
魔改spring-context-indexer
为了解决 A模块引入，B模块不引入，导致无法使用问题

复制spring-context-indexer源码，把生成的索引文件改成 `META-INF/spring-custom.components` ,修改的源码 `MetadataStore#METADATA_PATH`

使用方式：
1.自己打包 custom-indexer，引入到自己的项目中,【如果目录变了，需要修改resource/META-INF/services javax.annotation.processing.Processor 】
```      
<dependency>
     <groupId>org.example</groupId>
     <artifactId>custom-indexer</artifactId>
     <scope>system</scope>
     <version>1.0-SNAPSHOT</version>
     <systemPath>${project.basedir}/lib/custom-indexer-1.0-SNAPSHOT.jar</systemPath>
 </dependency>
 ```
2.在使用的项目中覆盖spring-context-core  CandidateComponentsIndexLoader 源码，使用同名类覆盖的方式
```
package org.springframework.context.index;


import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ConcurrentMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.core.SpringProperties;
import org.springframework.core.io.UrlResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.springframework.lang.Nullable;
import org.springframework.util.ConcurrentReferenceHashMap;

/**
 * Candidate components index loading mechanism for internal use within the framework.
 *
 * @author Stephane Nicoll
 * @since 5.0
 */
public final class CandidateComponentsIndexLoader {

    /**
     * The location to look for components.
     * <p>Can be present in multiple JAR files.
     */
    public static final String COMPONENTS_RESOURCE_LOCATION = "META-INF/spring-custom.components";

    /**
     * System property that instructs Spring to ignore the index, i.e.
     * to always return {@code null} from {@link #loadIndex(ClassLoader)}.
     * <p>The default is "false", allowing for regular use of the index. Switching this
     * flag to {@code true} fulfills a corner case scenario when an index is partially
     * available for some libraries (or use cases) but couldn't be built for the whole
     * application. In this case, the application context fallbacks to a regular
     * classpath arrangement (i.e. as no index was present at all).
     */
    public static final String IGNORE_INDEX = "spring.index.ignore";
    public static final String ENABLE_CUSTOM_INDEX = "customIndexer"; //自己魔改的

    private static final boolean shouldIgnoreIndex = SpringProperties.getFlag(IGNORE_INDEX);
    private static final boolean shouldEnableCustomIndex = SpringProperties.getFlag(ENABLE_CUSTOM_INDEX);

    private static final Log logger = LogFactory.getLog(CandidateComponentsIndexLoader.class);

    private static final ConcurrentMap<ClassLoader, CandidateComponentsIndex> cache =
            new ConcurrentReferenceHashMap<>();


    public CandidateComponentsIndexLoader() {
    }


    /**
     * Load and instantiate the {@link CandidateComponentsIndex} from
     * {@value #COMPONENTS_RESOURCE_LOCATION}, using the given class loader. If no
     * index is available, return {@code null}.
     * @param classLoader the ClassLoader to use for loading (can be {@code null} to use the default)
     * @return the index to use or {@code null} if no index was found
     * @throws IllegalArgumentException if any module index cannot
     * be loaded or if an error occurs while creating {@link CandidateComponentsIndex}
     */
    @Nullable
    public static CandidateComponentsIndex loadIndex(@Nullable ClassLoader classLoader) {
        ClassLoader classLoaderToUse = classLoader;
        if (classLoaderToUse == null) {
            classLoaderToUse = CandidateComponentsIndexLoader.class.getClassLoader();
        }
        return cache.computeIfAbsent(classLoaderToUse, CandidateComponentsIndexLoader::doLoadIndex);
    }

    @Nullable
    private static CandidateComponentsIndex doLoadIndex(ClassLoader classLoader) {
        if (shouldIgnoreIndex) {
            return null;
        }
        //魔改的
        if(!shouldEnableCustomIndex){
            return null;
        }
        try {
            Enumeration<URL> urls = classLoader.getResources(COMPONENTS_RESOURCE_LOCATION);
            if (!urls.hasMoreElements()) {
                return null;
            }
            List<Properties> result = new ArrayList<>();
            while (urls.hasMoreElements()) {
                URL url = urls.nextElement();
                Properties properties = PropertiesLoaderUtils.loadProperties(new UrlResource(url));
                result.add(properties);
            }
            if (logger.isDebugEnabled()) {
                logger.debug("Loaded " + result.size() + "] index(es)");
            }
            int totalCount = result.stream().mapToInt(Properties::size).sum();
            return (totalCount > 0 ? new CandidateComponentsIndex(result) : null);
        }
        catch (IOException ex) {
            throw new IllegalStateException("Unable to load indexes from location [" +
                    COMPONENTS_RESOURCE_LOCATION + "]", ex);
        }
    }

}

```
3.增加了一个配置启用覆盖类扫描 spring-custom.components，需要在 **spring.properties** 中配置 或者 加到命令行......
``` java -DcustomIndexer=true ........```
