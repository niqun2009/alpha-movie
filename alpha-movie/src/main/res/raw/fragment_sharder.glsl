#extension GL_OES_EGL_image_external : require
precision mediump float;
varying vec2 vTextureCoord;
uniform samplerExternalOES sTexture;
uniform samplerExternalOES sTextureAlpha;
void main() {
   vec4 color = texture2D(sTexture, vTextureCoord);
   vec4 colorAlpha = texture2D(sTextureAlpha, vTextureCoord);
   vec4 colorTrans = vec4(0., 0., 0., 0.);
   gl_FragColor = mix(colorTrans, color, colorAlpha.g);
}