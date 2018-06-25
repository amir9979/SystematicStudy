package fr.inria.astor.core.ingredientbased;

import java.util.List;

import org.apache.log4j.Logger;

import com.martiansoftware.jsap.JSAPException;

import fr.inria.astor.core.manipulation.filters.TargetElementProcessor;
import fr.inria.astor.core.setup.ConfigurationProperties;
import fr.inria.astor.core.solutionsearch.AstorCoreEngine;
import fr.inria.astor.core.solutionsearch.spaces.ingredients.IngredientPool;
import fr.inria.astor.core.solutionsearch.spaces.ingredients.IngredientSearchStrategy;
import fr.inria.astor.core.solutionsearch.spaces.ingredients.ingredientSearch.CloneIngredientSearchStrategy;
import fr.inria.astor.core.solutionsearch.spaces.ingredients.ingredientSearch.EfficientIngredientStrategy;
import fr.inria.astor.core.solutionsearch.spaces.ingredients.ingredientSearch.ProbabilisticIngredientStrategy;
import fr.inria.astor.core.solutionsearch.spaces.ingredients.scopes.GlobalBasicIngredientSpace;
import fr.inria.astor.core.solutionsearch.spaces.ingredients.scopes.LocalIngredientSpace;
import fr.inria.astor.core.solutionsearch.spaces.ingredients.scopes.PackageBasicFixSpace;
import fr.inria.astor.core.solutionsearch.spaces.ingredients.transformations.ClusterIngredientTransformation;
import fr.inria.astor.core.solutionsearch.spaces.ingredients.transformations.DefaultIngredientTransformation;
import fr.inria.astor.core.solutionsearch.spaces.ingredients.transformations.IngredientTransformationStrategy;
import fr.inria.astor.core.solutionsearch.spaces.ingredients.transformations.ProbabilisticTransformationStrategy;
import fr.inria.astor.core.solutionsearch.spaces.ingredients.transformations.RandomTransformationStrategy;
import fr.inria.main.evolution.ExtensionPoints;
import fr.inria.main.evolution.PlugInLoader;

/**
 * 
 * @author Matias Martinez
 *
 */
public class IngredientBasedPlugInLoader  {

	public IngredientBasedPlugInLoader() {
		super();
	}

	

	protected static Logger log = Logger.getLogger(PlugInLoader.class);
	
	@SuppressWarnings("rawtypes")
	protected void loadIngredientPool(AstorCoreEngine approach) throws JSAPException, Exception {
		IngredientBasedApproach ibra = (IngredientBasedApproach) approach;
		List<TargetElementProcessor<?>> ingredientProcessors = approach.getTargetElementProcessors();
		// The ingredients for build the patches
		IngredientPool ingredientspace = getIngredientPool(ingredientProcessors);

		ibra.setIngredientPool(ingredientspace);

	}

	public static IngredientPool getIngredientPool(List<TargetElementProcessor<?>> ingredientProcessors)
			throws JSAPException, Exception {
		String scope = ConfigurationProperties.properties.getProperty("scope");
		IngredientPool ingredientspace = null;
		if ("global".equals(scope)) {
			ingredientspace = (new GlobalBasicIngredientSpace(ingredientProcessors));
		} else if ("package".equals(scope)) {
			ingredientspace = (new PackageBasicFixSpace(ingredientProcessors));
		} else if ("local".equals(scope) || "file".equals(scope)) {
			ingredientspace = (new LocalIngredientSpace(ingredientProcessors));
		} else {
			ingredientspace = (IngredientPool) PlugInLoader.loadPlugin(ExtensionPoints.INGREDIENT_STRATEGY_SCOPE,
					new Class[] { List.class }, new Object[] { ingredientProcessors });

		}
		return ingredientspace;
	}

	@SuppressWarnings("rawtypes")
	protected static IngredientSearchStrategy loadIngredientSearchStrategy(AstorCoreEngine approach) throws Exception {

		IngredientBasedApproach ibra = (IngredientBasedApproach) approach;

		IngredientPool ingredientspace = ibra.getIngredientPool();

		IngredientSearchStrategy ingStrategy = null;

		String ingStrategySt = ConfigurationProperties.properties
				.getProperty(ExtensionPoints.INGREDIENT_SEARCH_STRATEGY.identifier);

		if (ingStrategySt != null) {

			if (ingStrategySt.equals("uniform-random")) {
				ingStrategy = new EfficientIngredientStrategy(ingredientspace);
			} else if (ingStrategySt.equals("name-probability-based")) {
				ingStrategy = new ProbabilisticIngredientStrategy(ingredientspace);
			} else if (ingStrategySt.equals("code-similarity-based")) {
				ingStrategy = new CloneIngredientSearchStrategy(ingredientspace);
			} else {
				ingStrategy = (IngredientSearchStrategy) PlugInLoader.loadPlugin(
						ExtensionPoints.INGREDIENT_SEARCH_STRATEGY, new Class[] { IngredientPool.class },
						new Object[] { ingredientspace });
			}
		} else {
			ingStrategy = new EfficientIngredientStrategy(ingredientspace);
		}

		return (ingStrategy);

	}

	@Deprecated
	protected void loadIngredientTransformationStrategy(AstorCoreEngine approach) throws Exception {
		
		IngredientTransformationStrategy ingredientTransformationStrategyLoaded = retrieveIngredientTransformationStrategy();
		IngredientBasedApproach ibra = (IngredientBasedApproach) approach;
		ibra.setIngredientTransformationStrategy(ingredientTransformationStrategyLoaded);
	}

	public static IngredientTransformationStrategy retrieveIngredientTransformationStrategy() throws Exception {
		IngredientTransformationStrategy ingredientTransformationStrategyLoaded = null;
		String ingredientTransformationStrategy = ConfigurationProperties.properties
				.getProperty(ExtensionPoints.INGREDIENT_TRANSFORM_STRATEGY.identifier);

		if (ingredientTransformationStrategy == null) {
			ingredientTransformationStrategyLoaded = (new DefaultIngredientTransformation());
		} else {// there is a value
			if (ingredientTransformationStrategy.equals("no-transformation")) {
				ingredientTransformationStrategyLoaded = (new DefaultIngredientTransformation());
			} else if (ingredientTransformationStrategy.equals("random-variable-replacement")) {
				ingredientTransformationStrategyLoaded = (new RandomTransformationStrategy());
			} else if (ingredientTransformationStrategy.equals("name-cluster-based")) {
				ingredientTransformationStrategyLoaded = (new ClusterIngredientTransformation());
			} else if (ingredientTransformationStrategy.equals("name-probability-based")) {
				ingredientTransformationStrategyLoaded = (new ProbabilisticTransformationStrategy());
			} else {
				ingredientTransformationStrategyLoaded = ((IngredientTransformationStrategy) PlugInLoader
						.loadPlugin(ExtensionPoints.INGREDIENT_TRANSFORM_STRATEGY));
			}
		}
		return ingredientTransformationStrategyLoaded;
	}

}