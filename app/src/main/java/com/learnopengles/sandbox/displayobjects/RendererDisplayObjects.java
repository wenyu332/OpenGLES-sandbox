package com.learnopengles.sandbox.displayobjects;

import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.os.SystemClock;
import android.util.Log;

import com.learnopengles.sandbox.objects.BufferManager;
import com.learnopengles.sandbox.objects.Cone;
import com.learnopengles.sandbox.objects.Cube;
import com.learnopengles.sandbox.objects.Cylinder;
import com.learnopengles.sandbox.objects.Ellipse;
import com.learnopengles.sandbox.objects.EllipseHelix;
import com.learnopengles.sandbox.objects.HeightMap;
import com.learnopengles.sandbox.objects.Sphere;
import com.learnopengles.sandbox.objects.Teapot;
import com.learnopengles.sandbox.objects.TeapotIBO;
import com.learnopengles.sandbox.objects.ToroidHelix;
import com.learnopengles.sandbox.objects.TriangleTest;
import com.learnopengles.sandbox.objects.XYZ;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/*
 * notes : the teapot needs fixing but the Kronos data also had issues with direct rendering...
 */
/*
 *   Alt-Enter to disable annoying Lint warnings...
 *
 *   MVP
 *     M - Model to World
 *     V - World to View
 *     P - View to Projection
 */

/**
 * This class implements our custom renderer. Note that the GL10 parameter passed in is unused for OpenGL ES 2.0
 * renderers -- the static class GLES20 is used instead.
 */
public class RendererDisplayObjects implements GLSurfaceView.Renderer {

    private XYZ mXYZ = new XYZ();

    private static String LOG_TAG = "Renderer";
    // update to add touch control - these are set by the SurfaceView class
    // These still work without volatile, but refreshes are not guaranteed to happen.
    public volatile float mDeltaX;
    public volatile float mDeltaY;
    public volatile float mDeltaTranslateX;
    public volatile float mDeltaTranslateY;
    public volatile float mScaleF = 1.0f;
    public volatile float mScaleDelta = 0f;

    private static int scaleCount = 0;
    private static boolean shrinking = true;
    private static int mHeight;
    private static int mWidth;


    /**
     * Used for debug logs.
     */
    private static final String TAG = "LessonCylRenderer";

    /**
     * Store the model matrix. This matrix is used to move models from object space (where each model can be thought
     * of being located at the center of the universe) to world space.
     */
    private float[] mModelMatrix = new float[16];

    /**
     * Store the view matrix. This can be thought of as our camera. This matrix transforms world space to eye space;
     * it positions things relative to our eye.
     */
    private float[] mViewMatrix = new float[16];

    /**
     * Store the projection matrix. This is used to project the scene onto a 2D viewport.
     */
    private float[] mProjectionMatrix = new float[16];

    /**
     * Allocate storage for the final combined matrix. This will be passed into the shader program.
     */
    private float[] mMVPMatrix = new float[16];

    /**
     * Stores a copy of the model matrix specifically for the light position.
     */
    private float[] mLightModelMatrix = new float[16];

    /**
     * This will be used to pass in the transformation matrix.
     */
    private int mMVPMatrixHandle;

    /**
     * This will be used to pass in the modelview matrix.
     */
    private int mMVMatrixHandle;

    /**
     * This will be used to pass in the light position.
     */
    private int mLightPosHandle;

    /**
     * This will be used to pass in model position information.
     */
    private int mPositionHandle;

    /**
     * This will be used to pass in model color information.
     */
    private int mColorHandle;

    /**
     * This will be used to pass in model normal information.
     */
    private int mNormalHandle;

    /**
     * How many bytes per float.
     */
    private final int mBytesPerFloat = 4;

    /**
     * Size of the position data in elements.
     */
    private final int mPositionDataSize = 3;

    /**
     * Size of the color data in elements.
     */
    private final int mColorDataSize = 4;

    /**
     * Size of the normal data in elements.
     */
    private final int mNormalDataSize = 3;

    /**
     * Used to hold a light centered on the origin in model space. We need a 4th coordinate so we can get translations to work when
     * we multiply this by our transformation matrices.
     */
    private final float[] mLightPosInModelSpace = new float[]{0.0f, 0.0f, 0.0f, 1.0f};

    /**
     * Used to hold the current position of the light in world space (after transformation via model matrix).
     */
    private final float[] mLightPosInWorldSpace = new float[4];

    /**
     * Used to hold the transformed position of the light in eye space (after transformation via modelview matrix)
     */
    private final float[] mLightPosInEyeSpace = new float[4];

    private boolean mUseVertexShaderProgram = true;
    /**
     * This is a handle to our per-vertex cube shading program.
     */
    private int mPerVertexProgramHandle;
    /**
     * This is a handle to our per-pixel cube shading program.
     */
    private int mPerPixelProgramHandle;

    private int mSelectedProgramHandle;

    private boolean mWireFrameRenderingFlag = false;
    private boolean mRenderOnlyIBO = true;

    /**
     * This is a handle to our light point program.
     */
    private int mPointProgramHandle;


    private ActivtyDisplayObjects mLessonCylActivity;
    private GLSurfaceView mGlSurfaceView;
    /**
     * A temporary matrix.
     */
    private float[] mTemporaryMatrix = new float[16];

    /**
     * Store the accumulated rotation.
     */
    private final float[] mAccumulatedRotation = new float[16];
    private final float[] mAccumulatedTranslation = new float[16];
    private final float[] mAccumulatedScaling = new float[16];

    /**
     * Store the current rotation.
     */
    private final float[] mIncrementalRotation = new float[16];
    private final float[] mCurrentTranslation = new float[16];
    private final float[] mCurrentScaling = new float[16];

    private Cube mCube;
    private Teapot mTeapot;
    private TeapotIBO mTeapotIBO;
    private HeightMap mHeightMap;
    private Sphere mSphere;
    private Cylinder mCylinder;
    private Ellipse mEllipse;
    private EllipseHelix mEllipseHelix;
    private ToroidHelix mToroidHelix;
    private Cone mCone;
    private TriangleTest mTriangleTest;

    private BufferManager mBufferManager;

    /*
     * Let's get started.
     */
    public RendererDisplayObjects(final ActivtyDisplayObjects lessonCylActivity, final GLSurfaceView glSurfaceView) {
        mLessonCylActivity = lessonCylActivity;
        mGlSurfaceView = glSurfaceView;
        mBufferManager = BufferManager.getInstance(lessonCylActivity);
        BufferManager.allocateInitialBuffer();
    }

    @Override
    public void onSurfaceCreated(GL10 glUnused, EGLConfig config) {

        // Set the background clear color to black.
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);

        // Use culling to remove back faces.
        GLES20.glEnable(GLES20.GL_CULL_FACE);

        // Enable depth testing
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);

        int glError;
        glError = GLES20.glGetError();
        if (glError != GLES20.GL_NO_ERROR) {
            Log.e(LOG_TAG, "GLERROR: " + glError);
        }
        // Position the eye in front of the origin.
        final float eyeX = 0.0f;
        final float eyeY = 0.0f;
        final float eyeZ = -0.5f;

        // We are looking toward the distance
        final float lookX = 0.0f;
        final float lookY = 0.0f;
        final float lookZ = -5.0f;

        // Set our up vector. This is where our head would be pointing were we holding the camera.
        final float upX = 0.0f;
        final float upY = 1.0f;
        final float upZ = 0.0f;

        // Set the view matrix. This matrix can be said to represent the camera position.
        // NOTE: In OpenGL 1, a ModelView matrix is used, which is a combination of a model and
        // view matrix. In OpenGL 2, we can keep track of these matrices separately if we choose.
        Matrix.setLookAtM(mViewMatrix, 0, eyeX, eyeY, eyeZ, lookX, lookY, lookZ, upX, upY, upZ);

        String vertexShader = mXYZ.getVertexShaderLesson2();
        String fragmentShader = mXYZ.getFragmentShaderLesson2();
        int vertexShaderHandle = compileShader(GLES20.GL_VERTEX_SHADER, vertexShader);
        int fragmentShaderHandle = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentShader);

        mPerVertexProgramHandle = createAndLinkProgram(vertexShaderHandle, fragmentShaderHandle,
                new String[]{"a_Position", "a_Color", "a_Normal"});

        /* add in a pixel shader from lesson 3 - switchable */
        vertexShader = mXYZ.getVertexShaderLesson3();
        fragmentShader = mXYZ.getFragmentShaderLesson3();
        vertexShaderHandle = compileShader(GLES20.GL_VERTEX_SHADER, vertexShader);
        fragmentShaderHandle = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentShader);
        mPerPixelProgramHandle = createAndLinkProgram(vertexShaderHandle, fragmentShaderHandle,
                new String[]{"a_Position", "a_Color", "a_Normal"});

        // Define a simple shader program for our point (the orbiting light source)
        final String pointVertexShader =
                "uniform mat4 u_MVPMatrix;      \n"
                        + "attribute vec4 a_Position;     \n"
                        + "void main()                    \n"
                        + "{                              \n"
                        + "   gl_Position = u_MVPMatrix   \n"
                        + "               * a_Position;   \n"
                        + "   gl_PointSize = 5.0;         \n"
                        + "}                              \n";

        final String pointFragmentShader =
                "precision mediump float;       \n"
                        + "void main()                    \n"
                        + "{                              \n"
                        + "   gl_FragColor = vec4(1.0,    \n"
                        + "   1.0, 1.0, 1.0);             \n"
                        + "}                              \n";

        final int pointVertexShaderHandle = compileShader(GLES20.GL_VERTEX_SHADER, pointVertexShader);
        final int pointFragmentShaderHandle = compileShader(GLES20.GL_FRAGMENT_SHADER, pointFragmentShader);
        mPointProgramHandle = createAndLinkProgram(pointVertexShaderHandle, pointFragmentShaderHandle,
                new String[]{"a_Position"});

        /*
         * begin the geometry assortment allocations
         */
        // float color[] = new float[] { 0.5f, 0.5f, 0.0f, 0.0f };
        float nice_color[] = new float[]{218f / 256f, 182f / 256f, 85f / 256f, 1.0f};
        float color[] = new float[]{0.0f, 0.4f, 0.0f, 1.0f};
        float color_red[] = new float[]{0.6f, 0.0f, 0.0f, 1.0f};
        float color_teapot_green[] = new float[]{0f, 0.3f, 0.0f, 1.0f};
        float color_teapot_red[] = new float[]{0.3f, 0.0f, 0.0f, 1.0f};
        float chimera_color[] = new float[]{229f / 256f, 196f / 256f, 153f / 256f, 1.0f};

        mCube = new Cube();
        mTeapot = new Teapot( color_teapot_green );
        mTeapotIBO = new TeapotIBO( color_teapot_red );
        mHeightMap = new HeightMap();

        mSphere = new Sphere(
			30, // slices
			0.5f, // radius
            color_teapot_green );

        // Cylinder notes
        //   3 - slices makes a prism
        //   4 - slices makes a cube
        mCylinder = new Cylinder(
                30, // slices
                0.25f, // radius
                .5f, // length
                color);

        mEllipse = new Ellipse(
                30, // slices
                0.25f, // radius
                .5f, // length
                color);

//        mEllipseHelix = new EllipseHelix(
//                mBufferManager,
//                10, // slices
//                .5f, // radius
//                .5f, // length
//                color);


        mToroidHelix = new ToroidHelix(
                mBufferManager,
                chimera_color);
        mBufferManager.transferToGl();

        // commit the vertices

        mCone = new Cone(
                50, // slices
                0.25f, // radius
                .5f, // length
                nice_color,
                color_red );

        mTriangleTest = new TriangleTest();

        // Initialize the modifier matrices
        Matrix.setIdentityM(mAccumulatedRotation, 0);
        Matrix.setIdentityM(mAccumulatedTranslation, 0);
        Matrix.setIdentityM(mAccumulatedScaling, 0);
    }

    @Override
    public void onSurfaceChanged(GL10 glUnused, int width, int height) {
        // Set the OpenGL viewport to the same size as the surface.
        GLES20.glViewport(0, 0, width, height);
        mWidth = width;
        mHeight = height;

        // Create a new perspective projection matrix. The height will stay the same
        // while the width will vary as per aspect ratio.
        final float ratio = (float) width / height;
        final float left = -ratio * mScaleF;
        final float right = ratio * mScaleF;
        final float bottom = -1.0f * mScaleF;
        final float top = 1.0f * mScaleF;
        final float near = 1.0f;
        // final float far = 20.0f;
        final float far = 10.0f;
        // final float far = 5.0f;  nothing visible

        Matrix.frustumM(mProjectionMatrix, 0, left, right, bottom, top, near, far);

        int glError;
        glError = GLES20.glGetError();
        if (glError != GLES20.GL_NO_ERROR) {
            Log.e(LOG_TAG, "GLERROR: " + glError);
        }
    }

    @Override
    public void onDrawFrame(GL10 glUnused) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        // Do a complete rotation every 10 seconds.
        long time = SystemClock.uptimeMillis() % 10000L;
        float angleInDegrees = (360.0f / 10000.0f) * ((int) time);
        float lightangleInDegrees = (360.0f / 7000.0f) * ((int) (SystemClock.uptimeMillis() % 7000L));

        // lightangleInDegrees = 0f;

        // this does a nice job of rotating the camera slowly to the right
        // (scene shifts circularly to the left)
        // HACK:
        // Matrix.rotateM(mViewMatrix, 0, 0.1f, 0.0f, 1.0f, 0.0f);

        // HACK: experiment with scaling
        // results:  view matrix does move all objects forward / backward
        //   in the view but they quickly run into the clip plane
        // (2) simple scaling of the projection matrix does nothing the model view, but
        // moves the clip plane toward the unchanged view of the model.
        // (3) reworking the logic in onSurfaceChanged does a good job of zooming in and out
        // (4) adding in a little translateM gives a good pan during the zoom
        //      the inverse translate is tricky to scale to get back to the same point.
        if (shrinking) {
            // Matrix.translateM(mViewMatrix, 0, -.011f, 0f, 0f);
            mScaleF -= 0.01f;
            // onSurfaceChanged(null, mWidth, mHeight);
            if (++scaleCount > 90) {
                scaleCount = 0;
                shrinking = false;
            }
        } else {
            // Matrix.translateM(mViewMatrix, 0, 1f / (989f / 1000f) * .01f, 0f, 0f);
            mScaleF += 0.01f;
            // onSurfaceChanged(null, mWidth, mHeight);
            if (++scaleCount > 90) {
                scaleCount = 0;
                shrinking = true;
            }
        }

        if (mScaleDelta != 0f) {
            mScaleF += mScaleDelta;
            onSurfaceChanged(null, mWidth, mHeight);  // adjusts view
            mScaleDelta = 0f;
        }

        // move the view as necessary if the user has shifted it manually
        Matrix.translateM(mViewMatrix, 0, mDeltaTranslateX, mDeltaTranslateY, 0.0f);
        mDeltaTranslateX = 0.0f;
        mDeltaTranslateY = 0.0f;

        // Set our per-vertex lighting program.
        if (mUseVertexShaderProgram) {
            mSelectedProgramHandle = mPerVertexProgramHandle;
        } else {
            mSelectedProgramHandle = mPerPixelProgramHandle;
        }

        GLES20.glUseProgram(mSelectedProgramHandle);
        // Set program handles for drawing.
        mMVPMatrixHandle = GLES20.glGetUniformLocation(mSelectedProgramHandle, "u_MVPMatrix");
        mMVMatrixHandle = GLES20.glGetUniformLocation(mSelectedProgramHandle, "u_MVMatrix");
        mLightPosHandle = GLES20.glGetUniformLocation(mSelectedProgramHandle, "u_LightPos");
        mPositionHandle = GLES20.glGetAttribLocation(mSelectedProgramHandle, "a_Position");
        mColorHandle = GLES20.glGetAttribLocation(mSelectedProgramHandle, "a_Color");
        mNormalHandle = GLES20.glGetAttribLocation(mSelectedProgramHandle, "a_Normal");

        int hack = 1; // orbit the light
        if (hack == 0) {
            // Calculate position of the light. Rotate and then push into the distance.
            Matrix.setIdentityM(mLightModelMatrix, 0);
            Matrix.translateM(mLightModelMatrix, 0, 0.0f, 0.0f, -5.0f);
            Matrix.rotateM(mLightModelMatrix, 0, lightangleInDegrees, 0.0f, 1.0f, 0.0f);
            Matrix.translateM(mLightModelMatrix, 0, 0.0f, 0.0f, 2.0f);// original

            // HACK: makes the orbit bigger
            // Matrix.translateM(mLightModelMatrix, 0, 0.0f, 0.0f, 5.0f);

            Matrix.multiplyMV(mLightPosInWorldSpace, 0, mLightModelMatrix, 0, mLightPosInModelSpace, 0);
            Matrix.multiplyMV(mLightPosInEyeSpace, 0, mViewMatrix, 0, mLightPosInWorldSpace, 0);

        } else { // fixed lighting position
            // Calculate position of the light. Push into the distance.
            Matrix.setIdentityM(mLightModelMatrix, 0);
            Matrix.translateM(mLightModelMatrix, 0, 0.0f, 0.0f, -1.0f);

            Matrix.multiplyMV(mLightPosInWorldSpace, 0, mLightModelMatrix, 0, mLightPosInModelSpace, 0);
            Matrix.multiplyMV(mLightPosInEyeSpace, 0, mViewMatrix, 0, mLightPosInWorldSpace, 0);
        }

        // GLES20.glClearDepth(1.0f);

        // Obj #1 upper left
        Matrix.setIdentityM(mModelMatrix, 0);
        Matrix.translateM(mModelMatrix, 0, -.75f, 1.0f, -2.5f);
        Matrix.scaleM(mModelMatrix, 0, 1.0f, 1.0f, 1.0f);
        do_matrix_setup();
        mCylinder.render(mPositionHandle, mColorHandle, mNormalHandle, mWireFrameRenderingFlag);

        // Obj #5 center
        Matrix.setIdentityM(mModelMatrix, 0);
        Matrix.translateM(mModelMatrix, 0, 0.0f, 1.0f, -2.5f);
        Matrix.scaleM(mModelMatrix, 0, .6f, .6f, .6f);
        do_matrix_setup();
        mSphere.render(mPositionHandle, mColorHandle, mNormalHandle, mWireFrameRenderingFlag);

        // Obj #3 upper right
        Matrix.setIdentityM(mModelMatrix, 0);
        Matrix.translateM(mModelMatrix, 0, 1.0f, .75f, -2.5f);
        Matrix.scaleM(mModelMatrix, 0, 3.5f, 3.5f, 3.5f);
        do_matrix_setup();
        mTeapotIBO.render(mPositionHandle, mColorHandle, mNormalHandle, mWireFrameRenderingFlag);

        // Obj #4 mid left
        Matrix.setIdentityM(mModelMatrix, 0);
        Matrix.translateM(mModelMatrix, 0, -1.0f, 0.0f, -2.5f);
        Matrix.scaleM(mModelMatrix, 0, .25f, .25f, .25f);
        do_matrix_setup();
        mCube.render(mPositionHandle, mColorHandle, mNormalHandle, mWireFrameRenderingFlag);

//        // Obj #5 center
//        Matrix.setIdentityM(mModelMatrix, 0);
//        Matrix.translateM(mModelMatrix, 0, 0.0f, 0.0f, -2.5f);
//        Matrix.scaleM(mModelMatrix, 0, .6f, .6f, .6f);
//        do_matrix_setup();
//        // mSphere.render(mPositionHandle, mColorHandle, mNormalHandle, mWireFrameRenderingFlag);
//
        // Obj #5 center
        Matrix.setIdentityM(mModelMatrix, 0);
        Matrix.translateM(mModelMatrix, 0, 0.0f, 0.0f, -2.5f);
        Matrix.scaleM(mModelMatrix, 0, .05f, .05f, .05f);
        // 5X large version - usefule for debugging
        // Matrix.scaleM(mModelMatrix, 0, .25f, .25f, .25f);
        do_matrix_setup();
        mBufferManager.render(mPositionHandle, mColorHandle, mNormalHandle, mWireFrameRenderingFlag);


        // Obj #6 mid right
        Matrix.setIdentityM(mModelMatrix, 0);
        Matrix.translateM(mModelMatrix, 0, 1.0f, -0.25f, -2.5f);
        Matrix.scaleM(mModelMatrix, 0, 3.5f, 3.5f, 3.5f);
        do_matrix_setup();
        if (!mRenderOnlyIBO) {
            mTeapot.render(mPositionHandle, mColorHandle, mNormalHandle, mWireFrameRenderingFlag);  // direct rendering
        }

        // Obj #7 bottom left
        Matrix.setIdentityM(mModelMatrix, 0);
        Matrix.translateM(mModelMatrix, 0, -1.0f, -1.0f, -2.5f);
        Matrix.scaleM(mModelMatrix, 0, .05f, .05f, .05f);
        do_matrix_setup();
        mHeightMap.render(mPositionHandle, mColorHandle, mNormalHandle, mWireFrameRenderingFlag);

        // Obj #2 middle
        Matrix.setIdentityM(mModelMatrix, 0);
        Matrix.translateM(mModelMatrix, 0, 0.0f, -1.0f, -2.5f);
        Matrix.scaleM(mModelMatrix, 0, 1.0f, 1.0f, 1.0f);
        do_matrix_setup();
        // mTriangleTest.render(mPositionHandle, mColorHandle, mNormalHandle, mWireFrameRenderingFlag);
        mEllipse.render(mPositionHandle, mColorHandle, mNormalHandle, mWireFrameRenderingFlag);

        // Obj #9 bottom right
        Matrix.setIdentityM(mModelMatrix, 0);
        Matrix.translateM(mModelMatrix, 0, 1.0f, -1.0f, -2.5f);
        Matrix.scaleM(mModelMatrix, 0, 0.9f, 0.9f, 0.9f);
        do_matrix_setup();
        mCone.render(mPositionHandle, mColorHandle, mNormalHandle, mWireFrameRenderingFlag);

        int glError;
        glError = GLES20.glGetError();
        if (glError != GLES20.GL_NO_ERROR) {
            Log.e(LOG_TAG, "GLERROR: " + glError);
        }
    }


    private void do_matrix_setup() {
        /*
         * Set a matrix that contains the additional *incremental* rotation
         * as indicated by the user touching the screen
         */
        Matrix.setIdentityM(mIncrementalRotation, 0);
        Matrix.rotateM(mIncrementalRotation, 0, mDeltaX, 0.0f, 1.0f, 0.0f);
        Matrix.rotateM(mIncrementalRotation, 0, mDeltaY, 1.0f, 0.0f, 0.0f);
        mDeltaX = 0.0f;
        mDeltaY = 0.0f;

        // Multiply the current rotation by the accumulated rotation, and then set the accumulated rotation to the result.
        Matrix.multiplyMM(mTemporaryMatrix, 0, mIncrementalRotation, 0, mAccumulatedRotation, 0);
        System.arraycopy(mTemporaryMatrix, 0, mAccumulatedRotation, 0, 16);

        // Rotate the object taking the overall rotation into account.
        Matrix.multiplyMM(mTemporaryMatrix, 0, mModelMatrix, 0, mAccumulatedRotation, 0);
        System.arraycopy(mTemporaryMatrix, 0, mModelMatrix, 0, 16);

        // This multiplies the view matrix by the model matrix, and stores
        // the result in the MVP matrix
        // (which currently contains model * view).
        Matrix.multiplyMM(mMVPMatrix, 0, mViewMatrix, 0, mModelMatrix, 0);

        // Pass in the modelview matrix.
        GLES20.glUniformMatrix4fv(mMVMatrixHandle, 1, false, mMVPMatrix, 0);

        // This multiplies the modelview matrix by the projection matrix,
        // and stores the result in the MVP matrix
        // (which now contains model * view * projection).
        Matrix.multiplyMM(mTemporaryMatrix, 0, mProjectionMatrix, 0, mMVPMatrix, 0);
        System.arraycopy(mTemporaryMatrix, 0, mMVPMatrix, 0, 16);

        // Pass in the combined matrix.
        GLES20.glUniformMatrix4fv(mMVPMatrixHandle, 1, false, mMVPMatrix, 0);

        // Pass in the light position in eye space.
        GLES20.glUniform3f(mLightPosHandle, mLightPosInEyeSpace[0], mLightPosInEyeSpace[1], mLightPosInEyeSpace[2]);

        int glError;
        glError = GLES20.glGetError();
        if (glError != GLES20.GL_NO_ERROR) {
            Log.e(LOG_TAG, "GLERROR: " + glError);
        }
    }

    /**
     * Draws a point representing the position of the light.
     */
    private void drawLight() {
        final int pointMVPMatrixHandle = GLES20.glGetUniformLocation(mPointProgramHandle, "u_MVPMatrix");
        final int pointPositionHandle = GLES20.glGetAttribLocation(mPointProgramHandle, "a_Position");

        // Pass in the position.
        GLES20.glVertexAttrib3f(pointPositionHandle, mLightPosInModelSpace[0], mLightPosInModelSpace[1], mLightPosInModelSpace[2]);

        // Since we are not using a buffer object, disable vertex arrays for this attribute.
        GLES20.glDisableVertexAttribArray(pointPositionHandle);

        // Pass in the transformation matrix.
        Matrix.multiplyMM(mMVPMatrix, 0, mViewMatrix, 0, mLightModelMatrix, 0);
        Matrix.multiplyMM(mMVPMatrix, 0, mProjectionMatrix, 0, mMVPMatrix, 0);
        GLES20.glUniformMatrix4fv(pointMVPMatrixHandle, 1, false, mMVPMatrix, 0);

        // Draw the point.
        GLES20.glDrawArrays(GLES20.GL_POINTS, 0, 1);
    }

    /**
     * Helper function to compile a shader.
     *
     * @param shaderType   The shader type.
     * @param shaderSource The shader source code.
     * @return An OpenGL handle to the shader.
     */
    private int compileShader(final int shaderType, final String shaderSource) {
        int shaderHandle = GLES20.glCreateShader(shaderType);

        if (shaderHandle != 0) {
            // Pass in the shader source.
            GLES20.glShaderSource(shaderHandle, shaderSource);

            // Compile the shader.
            GLES20.glCompileShader(shaderHandle);

            // Get the compilation status.
            final int[] compileStatus = new int[1];
            GLES20.glGetShaderiv(shaderHandle, GLES20.GL_COMPILE_STATUS, compileStatus, 0);

            // If the compilation failed, delete the shader.
            if (compileStatus[0] == 0) {
                Log.e(TAG, "Error compiling shader: " + GLES20.glGetShaderInfoLog(shaderHandle));
                GLES20.glDeleteShader(shaderHandle);
                shaderHandle = 0;
            }
        }

        if (shaderHandle == 0) {
            throw new RuntimeException("Error creating shader.");
        }

        return shaderHandle;
    }

    /**
     * Helper function to compile and link a program.
     *
     * @param vertexShaderHandle   An OpenGL handle to an already-compiled vertex shader.
     * @param fragmentShaderHandle An OpenGL handle to an already-compiled fragment shader.
     * @param attributes           Attributes that need to be bound to the program.
     * @return An OpenGL handle to the program.
     */
    private int createAndLinkProgram(final int vertexShaderHandle, final int fragmentShaderHandle, final String[] attributes) {
        int programHandle = GLES20.glCreateProgram();

        if (programHandle != 0) {
            // Bind the vertex shader to the program.
            GLES20.glAttachShader(programHandle, vertexShaderHandle);

            // Bind the fragment shader to the program.
            GLES20.glAttachShader(programHandle, fragmentShaderHandle);

            // Bind attributes
            if (attributes != null) {
                final int size = attributes.length;
                for (int i = 0; i < size; i++) {
                    GLES20.glBindAttribLocation(programHandle, i, attributes[i]);
                }
            }

            // Link the two shaders together into a program.
            GLES20.glLinkProgram(programHandle);

            // Get the link status.
            final int[] linkStatus = new int[1];
            GLES20.glGetProgramiv(programHandle, GLES20.GL_LINK_STATUS, linkStatus, 0);

            // If the link failed, delete the program.
            if (linkStatus[0] == 0) {
                Log.e(TAG, "Error compiling program: " + GLES20.glGetProgramInfoLog(programHandle));
                GLES20.glDeleteProgram(programHandle);
                programHandle = 0;
            }
        }

        if (programHandle == 0) {
            throw new RuntimeException("Error creating program.");
        }

        return programHandle;
    }

    public void toggleShader() {
        if (mUseVertexShaderProgram) {
            mUseVertexShaderProgram = false;
            mLessonCylActivity.updateShaderStatus(false);
        } else {
            mUseVertexShaderProgram = true;
            mLessonCylActivity.updateShaderStatus(true);
        }
    }

    public void toggleWireframeFlag() {
        if (mWireFrameRenderingFlag) {
            mWireFrameRenderingFlag = false;
            mLessonCylActivity.updateWireframeStatus(false);
        } else {
            mWireFrameRenderingFlag = true;
            mLessonCylActivity.updateWireframeStatus(true);
        }
    }

    public void toggleRenderIBOFlag() {
        if (mRenderOnlyIBO) {
            mRenderOnlyIBO = false;
            mLessonCylActivity.updateRenderOnlyIBOStatus(false);
        } else {
            mRenderOnlyIBO = true;
            mLessonCylActivity.updateRenderOnlyIBOStatus(true);
        }
    }
}
