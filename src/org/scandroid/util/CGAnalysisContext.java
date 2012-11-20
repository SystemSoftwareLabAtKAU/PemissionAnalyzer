/*
 *
 * Copyright (c) 2009-2012,
 *
 *  Galois, Inc. (Aaron Tomb <atomb@galois.com>, Rogan Creswick <creswick@galois.com>)
 *  Steve Suh    <suhsteve@gmail.com>
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * 3. The names of the contributors may not be used to endorse or promote
 * products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 *
 *
 */
package org.scandroid.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.scandroid.domain.CodeElement;
import org.scandroid.domain.FieldElement;
import org.scandroid.domain.InstanceKeyElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Queues;
import com.google.common.collect.Sets;
import com.ibm.wala.classLoader.DexIRFactory;
import com.ibm.wala.classLoader.IField;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.dataflow.IFDS.ICFGSupergraph;
import com.ibm.wala.dataflow.IFDS.ISupergraph;
import com.ibm.wala.ipa.callgraph.AnalysisCache;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.Entrypoint;
import com.ibm.wala.ipa.callgraph.impl.DefaultContextSelector;
import com.ibm.wala.ipa.callgraph.impl.Everywhere;
import com.ibm.wala.ipa.callgraph.impl.PartialCallGraph;
import com.ibm.wala.ipa.callgraph.propagation.ConcreteTypeKey;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.callgraph.propagation.PointerAnalysis;
import com.ibm.wala.ipa.callgraph.propagation.PointerKey;
import com.ibm.wala.ipa.callgraph.propagation.SSAPropagationCallGraphBuilder;
import com.ibm.wala.ipa.cfg.BasicBlockInContext;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ssa.IRFactory;
import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.ssa.SSACFG;
import com.ibm.wala.ssa.SSACFG.BasicBlock;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.Predicate;
import com.ibm.wala.util.graph.Graph;
import com.ibm.wala.util.graph.GraphSlicer;
import com.ibm.wala.util.intset.OrdinalSet;
import com.ibm.wala.util.warnings.Warning;
import com.ibm.wala.util.warnings.Warnings;

/**
 * @author acfoltzer
 * 
 *         Represents an analysis context after the call graph, pointer
 *         analysis, and supergraphs have been generated. This is separated from
 *         AndroidAnalysisContext since these depend on the entrypoints for
 *         analysis in a way that is not likely reusable across all analyses of
 *         a particular classpath
 */
public class CGAnalysisContext<E extends ISSABasicBlock> {
	private static final Logger logger = LoggerFactory
			.getLogger(CGAnalysisContext.class);

	public final AndroidAnalysisContext analysisContext;

	private List<Entrypoint> entrypoints;
	public CallGraph cg;
	public PointerAnalysis pa;
	public ISupergraph<BasicBlockInContext<E>, CGNode> graph;

	public Graph<CGNode> oneLevelGraph;
	public Graph<CGNode> systemToApkGraph;
	public Graph<CGNode> partialGraph;

	public CGAnalysisContext(AndroidAnalysisContext analysisContext,
			IEntryPointSpecifier specifier) throws IOException {
		this(analysisContext, specifier, new ArrayList<InputStream>());
	}

	public CGAnalysisContext(AndroidAnalysisContext analysisContext,
			IEntryPointSpecifier specifier,
			Collection<InputStream> extraSummaries) throws IOException {
		
		this.analysisContext = analysisContext;
		final AnalysisScope scope = analysisContext.getScope();
		final ClassHierarchy cha = analysisContext.getClassHierarchy();
		final ISCanDroidOptions options = analysisContext.getOptions();

		entrypoints = specifier.specify(analysisContext);
		AnalysisOptions analysisOptions = new AnalysisOptions(scope,
				entrypoints);
		for (Entrypoint e : entrypoints) {
			logger.debug("Entrypoint: " + e);
		}
		analysisOptions.setReflectionOptions(options.getReflectionOptions());

		AnalysisCache cache = new AnalysisCache(
				(IRFactory<IMethod>) new DexIRFactory());

		SSAPropagationCallGraphBuilder cgb;

		if (null != options.getSummariesURI() ) {
			extraSummaries.add(new FileInputStream(new File(options.getSummariesURI())));
		}
		
		cgb = AndroidAnalysisContext.makeZeroCFABuilder(analysisOptions, cache,
				cha, scope, new DefaultContextSelector(analysisOptions, cha), null,
				extraSummaries, null);

		if (analysisContext.getOptions().cgBuilderWarnings()) {
			// CallGraphBuilder construction warnings
			for (Iterator<Warning> wi = Warnings.iterator(); wi.hasNext();) {
				Warning w = wi.next();
				logger.warn(w.getMsg());
			}
		}
		Warnings.clear();

		logger.info("*************************");
		logger.info("* Building Call Graph   *");
		logger.info("*************************");

		boolean graphBuilt = true;
		try {
			cg = cgb.makeCallGraph(cgb.getOptions());
		} catch (Exception e) {
			graphBuilt = false;
			if (!options.testCGBuilder()) {
				throw new RuntimeException(e);
			} else {
				e.printStackTrace();
			}
		}

		if (options.testCGBuilder()) {
			// TODO: this is too specialized for cmd-line apps
			int status = graphBuilt ? 0 : 1;
			System.exit(status);
		}

		// makeCallGraph warnings
		for (Iterator<Warning> wi = Warnings.iterator(); wi.hasNext();) {
			Warning w = wi.next();
			logger.warn(w.getMsg());
		}
		Warnings.clear();

		pa = cgb.getPointerAnalysis();
		partialGraph = GraphSlicer.prune(cg, new Predicate<CGNode>() {
			@Override
			// CallGraph composed of APK nodes
			public boolean test(CGNode node) {
				return LoaderUtils.fromLoader(node,
						ClassLoaderReference.Application)
						|| node.getMethod().isSynthetic();
			}
		});
		if (options.includeLibrary()) {
			graph = (ISupergraph) ICFGSupergraph.make(cg, cache);
		} else {

			Collection<CGNode> nodes = Sets.newHashSet();
			for (Iterator<CGNode> nIter = partialGraph.iterator(); nIter.hasNext();) {
				nodes.add(nIter.next());
			}
			CallGraph pcg = PartialCallGraph.make(cg, cg.getEntrypointNodes(),
					nodes);
			graph = (ISupergraph) ICFGSupergraph.make(pcg, cache);
		}
		
		oneLevelGraph = GraphSlicer.prune(cg, new Predicate<CGNode>() {
			@Override
			public boolean test(CGNode node) {
				// Node in APK
				if (LoaderUtils.fromLoader(node,
						ClassLoaderReference.Application)) {
					return true;
				} else {
					Iterator<CGNode> n = cg.getPredNodes(node);
					while (n.hasNext()) {
						// Primordial node has a successor in APK
						if (LoaderUtils.fromLoader(n.next(),
								ClassLoaderReference.Application))
							return true;
					}
					n = cg.getSuccNodes(node);
					while (n.hasNext()) {
						// Primordial node has a predecessor in APK
						if (LoaderUtils.fromLoader(n.next(),
								ClassLoaderReference.Application))
							return true;
					}
					// Primordial node with no direct successors or predecessors
					// to APK code
					return false;
				}
			}
		});

		systemToApkGraph = GraphSlicer.prune(cg, new Predicate<CGNode>() {
			@Override
			public boolean test(CGNode node) {

				if (LoaderUtils.fromLoader(node,
						ClassLoaderReference.Primordial)) {
					Iterator<CGNode> succs = cg.getSuccNodes(node);
					while (succs.hasNext()) {
						CGNode n = succs.next();

						if (LoaderUtils.fromLoader(n,
								ClassLoaderReference.Application)) {
							return true;
						}
					}
					// Primordial method, with no link to APK code:
					return false;
				} else if (LoaderUtils.fromLoader(node,
						ClassLoaderReference.Application)) {
					// see if this is an APK method that was
					// invoked by a Primordial method:
					Iterator<CGNode> preds = cg.getPredNodes(node);
					while (preds.hasNext()) {
						CGNode n = preds.next();

						if (LoaderUtils.fromLoader(n,
								ClassLoaderReference.Primordial)) {
							return true;
						}
					}
					// APK code, no link to Primordial:
					return false;
				}

				// who knows, not interesting:
				return false;
			}
		});

		if (options.pdfCG())
			GraphUtil.makeCG(this);
		if (options.pdfPartialCG())
			GraphUtil.makePCG(this);
		if (options.pdfOneLevelCG())
			GraphUtil.makeOneLCG(this);
		if (options.systemToApkCG())
			GraphUtil.makeSystemToAPKCG(this);

		if (options.stdoutCG()) {
			for (Iterator<CGNode> nodeI = cg.iterator(); nodeI.hasNext();) {
				CGNode node = nodeI.next();

				logger.debug("CGNode: " + node);
				for (Iterator<CGNode> succI = cg.getSuccNodes(node); 
				     succI.hasNext();) {
					
					logger.debug("\tSuccCGNode: "
							+ succI.next().getMethod().getSignature());
				}
			}
		}
		for (Iterator<CGNode> nodeI = cg.iterator(); nodeI.hasNext();) {
			CGNode node = nodeI.next();
			if (node.getMethod().isSynthetic()) {
				logger.trace("Synthetic Method: {}", node.getMethod()
						.getSignature());
				logger.trace("{}", node.getIR().getControlFlowGraph()
						.toString());
				SSACFG ssaCFG = node.getIR().getControlFlowGraph();
				int totalBlocks = ssaCFG.getNumberOfNodes();
				for (int i = 0; i < totalBlocks; i++) {
					logger.trace("BLOCK #{}", i);
					BasicBlock bb = ssaCFG.getBasicBlock(i);

					for (SSAInstruction ssaI : bb.getAllInstructions()) {
						logger.trace("\tInstruction: {}", ssaI);
					}
				}
			}
		}
	}
	
	/**
	 * @param rootIK
	 * @return a set of all code elements that might refer to this object or one
	 *         of its fields (recursively)
	 */
	public Set<CodeElement> codeElementsForInstanceKey(InstanceKey rootIK) {
		Set<CodeElement> elts = Sets.newHashSet();
		Deque<InstanceKey> iks = Queues.newArrayDeque();
		iks.push(rootIK);

		while (!iks.isEmpty()) {
			InstanceKey ik = iks.pop();
			logger.debug("getting code elements for {}", ik);
			elts.add(new InstanceKeyElement(ik));
			// only ask for fields if this is a class
			if (ik.getConcreteType().isArrayClass()) {
				continue;
			}				
			for (IField field : ik.getConcreteType().getAllInstanceFields()) {
				logger.debug("adding elements for field {}", field);
				final TypeReference fieldTypeRef = field
						.getFieldTypeReference();
				elts.add(new FieldElement(ik, field.getReference()));
				if (fieldTypeRef.isPrimitiveType()) {
					elts.add(new FieldElement(ik, field.getReference()));
				} else if (fieldTypeRef.isArrayType()) {
					PointerKey pk = pa.getHeapModel()
							.getPointerKeyForInstanceField(ik, field);
					final OrdinalSet<InstanceKey> pointsToSet = pa
							.getPointsToSet(pk);
					if (pointsToSet.isEmpty()) {
						logger.debug("pointsToSet empty for array field, creating InstanceKey manually");
						InstanceKey fieldIK = new ConcreteTypeKey(pa
								.getClassHierarchy().lookupClass(fieldTypeRef));
						final InstanceKeyElement elt = new InstanceKeyElement(
								fieldIK);
						elts.add(elt);
					} else {
						for (InstanceKey fieldIK : pointsToSet) {
							final InstanceKeyElement elt = new InstanceKeyElement(
									fieldIK);
							elts.add(elt);
						}
					}
				} else if (fieldTypeRef.isReferenceType()) {
					PointerKey pk = pa.getHeapModel()
							.getPointerKeyForInstanceField(ik, field);
					final OrdinalSet<InstanceKey> pointsToSet = pa
							.getPointsToSet(pk);
					if (pointsToSet.isEmpty() && !analysisContext.getClassHierarchy().isInterface(fieldTypeRef)) {
						logger.debug("pointsToSet empty for reference field, creating InstanceKey manually");
						InstanceKey fieldIK = new ConcreteTypeKey(
								analysisContext.getClassHierarchy().lookupClass(fieldTypeRef));
						final InstanceKeyElement elt = new InstanceKeyElement(
								fieldIK);
						if (!elts.contains(elt)) {
							elts.add(elt);
							iks.push(fieldIK);
						}
					} else {
						for (InstanceKey fieldIK : pointsToSet) {
							final InstanceKeyElement elt = new InstanceKeyElement(
									fieldIK);
							if (!elts.contains(elt)) {
								elts.add(elt);
								iks.push(fieldIK);
							}
						}
					}
				} else {
					logger.warn("unknown field type {}", field);
				}
			}
		}
		return elts;
	}

	public ISCanDroidOptions getOptions() {
		return analysisContext.getOptions();
	}

	public ClassHierarchy getClassHierarchy() {
		return analysisContext.getClassHierarchy();
	}

	public AnalysisScope getScope() {
		return analysisContext.getScope();
	}

	public List<Entrypoint> getEntrypoints() {
		return entrypoints;
	}

	public CGNode nodeForMethod(IMethod method) {
		return cg.getNode(method, Everywhere.EVERYWHERE);
	}
}
