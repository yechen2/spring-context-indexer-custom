/*
 * Copyright 2002-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package customIndexer;


import javax.annotation.processing.Completion;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import java.io.IOException;
import java.util.*;

/**
 * Annotation {@link Processor} that writes {@link CandidateComponentsMetadata}
 * file for spring components.
 */
public class CandidateComponentsIndexer implements Processor {

	private static final Set<ElementKind> TYPE_KINDS =
			Collections.unmodifiableSet(EnumSet.of(ElementKind.CLASS, ElementKind.INTERFACE));

	private MetadataStore metadataStore;

	private MetadataCollector metadataCollector;

	private TypeHelper typeHelper;

	private List<StereotypesProvider> stereotypesProviders;


	public CandidateComponentsIndexer() {
	}

	@Override
	public Set<String> getSupportedOptions() {
		return Collections.emptySet();
	}

	@Override
	public Set<String> getSupportedAnnotationTypes() {
		return Collections.singleton("*");
	}

	@Override
	public SourceVersion getSupportedSourceVersion() {
		return SourceVersion.latest();
	}

	@Override
	public synchronized void init(ProcessingEnvironment env) {
		this.stereotypesProviders = getStereotypesProviders(env);
		this.typeHelper = new TypeHelper(env);
		this.metadataStore = new MetadataStore(env);
		this.metadataCollector = new MetadataCollector(env, this.metadataStore.readMetadata());
	}

	@Override
	public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
		this.metadataCollector.processing(roundEnv);
		roundEnv.getRootElements().forEach(this::processElement);
		if (roundEnv.processingOver()) {
			writeMetaData();
		}
		return false;
	}

	@Override
	public Iterable<? extends Completion> getCompletions(
			Element element, AnnotationMirror annotation, ExecutableElement member, String userText) {

		return Collections.emptyList();
	}


	private List<StereotypesProvider> getStereotypesProviders(ProcessingEnvironment env) {
		List<StereotypesProvider> result = new ArrayList<>();
		TypeHelper typeHelper = new TypeHelper(env);
		result.add(new IndexedStereotypesProvider(typeHelper));
		result.add(new StandardStereotypesProvider(typeHelper));
		result.add(new PackageInfoStereotypesProvider());
		return result;
	}

	private void processElement(Element element) {
		addMetadataFor(element);
		staticTypesIn(element.getEnclosedElements()).forEach(this::processElement);
	}

	private void addMetadataFor(Element element) {
		Set<String> stereotypes = new LinkedHashSet<>();
		this.stereotypesProviders.forEach(p -> stereotypes.addAll(p.getStereotypes(element)));
		if (!stereotypes.isEmpty()) {
			this.metadataCollector.add(new ItemMetadata(this.typeHelper.getType(element), stereotypes));
		}
	}

	private void writeMetaData() {
		CandidateComponentsMetadata metadata = this.metadataCollector.getMetadata();
		if (!metadata.getItems().isEmpty()) {
			try {
				this.metadataStore.writeMetadata(metadata);
			}
			catch (IOException ex) {
				throw new IllegalStateException("Failed to write metadata", ex);
			}
		}
	}

	private static List<TypeElement> staticTypesIn(Iterable<? extends Element> elements) {
		List<TypeElement> list = new ArrayList<>();
		for (Element element : elements) {
			if (TYPE_KINDS.contains(element.getKind()) && element.getModifiers().contains(Modifier.STATIC)) {
				list.add(TypeElement.class.cast(element));
			}
		}
		return list;
	}

}
