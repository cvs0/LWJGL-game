/**
 * CvsEngine
 *
 * @author cvs0
 * @version 1.0.0
 *
 * @license
 * MIT License
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this
 * software and associated documentation files (the "Software"), to deal in the Software
 * without restriction, including without limitation the rights to use, copy, modify, merge,
 * publish, distribute, sublicense, and/or sell copies of the Software, and to permit
 * persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies
 * or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED,
 * INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR
 * PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE
 * FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
 * OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * DEALINGS IN THE SOFTWARE.
 */

package engine.renderEngine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GLContext;
import org.lwjgl.util.vector.Matrix4f;
import org.lwjgl.util.vector.Vector4f;

import engine.entities.Camera;
import engine.entities.Entity;
import engine.entities.Light;
import engine.models.TexturedModel;
import engine.normalMappingRenderer.NormalMappingRenderer;
import engine.shaders.StaticShader;
import engine.shaders.TerrainShader;
import engine.skybox.SkyboxRenderer;
import engine.terrains.Terrain;

public class MasterRenderer {
	
	private static final float FOV = 70;
	private static final float NEAR_PLANE = 0.1f;
	private static final float FAR_PLANE = 1000;
	
	public static final float RED = 0.1f;
	public static final float GREEN = 0.4f;
	public static final float BLUE = 0.2f;
	
	private StaticShader shader = new StaticShader();
	
	private Matrix4f projectionMatrix;
	
	private EntityRenderer renderer;
	
	private TerrainRenderer terrainRenderer;
	private TerrainShader terrainShader = new TerrainShader();
	
	private NormalMappingRenderer normalMapRenderer;
	
	private Map<TexturedModel, List<Entity>> entities = new HashMap<TexturedModel, List<Entity>>();
	private Map<TexturedModel, List<Entity>> normalMapEntities = new HashMap<TexturedModel, List<Entity>>();
	private List<Terrain> terrains = new ArrayList<Terrain>();
	
	private SkyboxRenderer skyboxRenderer;
	
	/**
	 * Constructs a MasterRenderer with the given loader. Initializes various rendering components and settings.
	 *
	 * @param loader The loader responsible for loading resources.
	 */
	public MasterRenderer(Loader loader) {
	    enableCulling();
	    
	    createProjectionMatrix();
	    renderer = new EntityRenderer(shader, projectionMatrix);
	    terrainRenderer = new TerrainRenderer(terrainShader, projectionMatrix);
	    skyboxRenderer = new SkyboxRenderer(loader, projectionMatrix);
	    normalMapRenderer = new NormalMappingRenderer(projectionMatrix);
	}

	/**
	 * Retrieves the projection matrix used for rendering scenes.
	 *
	 * @return The projection matrix.
	 */
	public Matrix4f getProjectionMatrix() {
	    return projectionMatrix;
	}

	
	/**
	 * Renders the entire scene, including entities, terrains, and the skybox, with the specified parameters.
	 *
	 * @param entities      The list of entities to render.
	 * @param normalEntities The list of entities with normal maps to render.
	 * @param terrains      The list of terrains to render.
	 * @param lights        The list of light sources in the scene.
	 * @param camera        The camera view used for rendering.
	 * @param clipPlane     The clipping plane used for water rendering.
	 */
	public void renderScene(List<Entity> entities, List<Entity> normalEntities, List<Terrain> terrains,
	        List<Light> lights, Camera camera, Vector4f clipPlane) {
		for(Terrain terrain : terrains) {
			processTerrain(terrain);
		}
		
		for(Entity entity : entities) {
			processEntity(entity);
		}
		
		for(Entity entity : normalEntities) {
			processNormalMapEntity(entity);
		}
		
		render(lights, camera, clipPlane);
	}
	
	/**
	 * Enables face culling to improve rendering performance. Call this method before rendering objects with culling enabled.
	 */
	public static void enableCulling() {
	    if (!GLContext.getCapabilities().OpenGL11) {
	        System.err.println("OpenGL 1.1 is required for face culling, but it's not supported.");
	        return;
	    }

	    GL11.glEnable(GL11.GL_CULL_FACE);
	    GL11.glCullFace(GL11.GL_BACK);
	}

	/**
	 * Disables face culling. Call this method when rendering objects without culling.
	 */
	public static void disableCulling() {
	    if (!GLContext.getCapabilities().OpenGL11) {
	        System.err.println("OpenGL 1.1 is required to disable face culling, but it's not supported.");
	        return;
	    }

	    GL11.glDisable(GL11.GL_CULL_FACE);
	}

	/**
	 * Renders entities and terrain with the specified light source and camera view.
	 *
	 * @param sun    The light source in the scene.
	 * @param camera The camera view used for rendering.
	 */
	public void render(List<Light> lights, Camera camera, Vector4f clipPlane) {
		prepare();
		shader.start();
		shader.loadClipPlane(clipPlane);
		shader.loadSkyColour(RED, GREEN, BLUE);
		shader.loadLights(lights);
		shader.loadViewMatrix(camera);
		renderer.render(entities);
		shader.stop();
		normalMapRenderer.render(normalMapEntities, clipPlane, lights, camera);
		terrainShader.start();
		terrainShader.loadClipPlane(clipPlane);
		terrainShader.loadSkyColour(RED, GREEN, BLUE);
		terrainShader.loadLights(lights);
		terrainShader.loadViewMatrix(camera);
		terrainRenderer.render(terrains);
		terrainShader.stop();
		skyboxRenderer.render(camera, RED, GREEN, BLUE);
		terrains.clear();
		entities.clear();
		normalMapEntities.clear();
	}
	

	/**
	 * Processes a terrain for rendering. Add terrains to the list to be rendered.
	 *
	 * @param terrain The terrain to be processed and rendered.
	 */
	public void processTerrain(Terrain terrain) {
		terrains.add(terrain);
	}
	
	/**
	 * Processes an entity for rendering. Add entities to the list to be rendered.
	 *
	 * @param entity The entity to be processed and rendered.
	 */
	public void processEntity(Entity entity) {
	    if (entity == null) {
	        return;
	    }

	    TexturedModel entityModel = entity.getModel();

	    if (entityModel == null) {
	        return;
	    }

	    List<Entity> batch = entities.get(entityModel);

	    if (batch != null) {
	        batch.add(entity);
	    } else {
	        List<Entity> newBatch = new ArrayList<>();

	        if (entityModel != null) {
	            newBatch.add(entity);
	            entities.put(entityModel, newBatch);
	        }
	    }
	}
	
	/**
	 * Processes a normal-mapped entity for rendering. This method adds the entity to the list
	 * of entities using normal mapping for rendering.
	 *
	 * @param entity The normal-mapped entity to be processed and rendered.
	 */
	public void processNormalMapEntity(Entity entity) {
	    if (entity == null) {
	        return;
	    }

	    TexturedModel entityModel = entity.getModel();

	    if (entityModel == null) {
	        return;
	    }

	    List<Entity> batch = normalMapEntities.get(entityModel);

	    if (batch != null) {
	        batch.add(entity);
	    } else {
	        List<Entity> newBatch = new ArrayList<>();

	        if (entityModel != null) {
	            newBatch.add(entity);
	            normalMapEntities.put(entityModel, newBatch);
	        }
	    }
	}


	/**
	 * Cleans up resources used by the master renderer. Should be called when rendering is finished.
	 */
	public void cleanUp() {
	    try {
	        if (shader != null) {
	            shader.cleanUp();
	        }
	        
	        if (terrainShader != null) {
	            terrainShader.cleanUp();
	        }
	        
	        if(normalMapRenderer != null) {
	        	normalMapRenderer.cleanUp();
	        }
	    } catch (Exception e) {
	        e.printStackTrace();
	    }
	}
	
	/**
	 * Prepares OpenGL for rendering by enabling depth testing and clearing the color and depth buffers.
	 */
	public void prepare() {
	    try {
	        GL11.glEnable(GL11.GL_DEPTH_TEST);
	        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
	        GL11.glClearColor(RED, GREEN, BLUE, 1);
	    } catch (Exception e) {
	        e.printStackTrace();
	    }
	}

	/**
	 * Creates the projection matrix based on the display size, field of view, and near and far planes.
	 */
	private void createProjectionMatrix() {
	    if (Display.getWidth() <= 0 || Display.getHeight() <= 0 || FOV <= 0 || FAR_PLANE <= NEAR_PLANE) {
	        throw new IllegalArgumentException("Invalid parameters for projection matrix");
	    }

	    float aspectRatio = (float) Display.getWidth() / (float) Display.getHeight();
	    if (aspectRatio == 0.0f) {
	        throw new IllegalArgumentException("Invalid aspect ratio for projection matrix");
	    }

	    float yScale = (float) (1f / Math.tan(Math.toRadians(FOV / 2f))) * aspectRatio;
	    float xScale = yScale / aspectRatio;
	    float frustumLength = FAR_PLANE - NEAR_PLANE;

	    projectionMatrix = new Matrix4f();
	    projectionMatrix.m00 = xScale;
	    projectionMatrix.m11 = yScale;
	    projectionMatrix.m22 = -((FAR_PLANE + NEAR_PLANE) / frustumLength);
	    projectionMatrix.m23 = -1;
	    projectionMatrix.m32 = -((2 * NEAR_PLANE * FAR_PLANE) / frustumLength);
	    projectionMatrix.m33 = 0;
	}
}
