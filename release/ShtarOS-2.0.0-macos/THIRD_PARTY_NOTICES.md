# Third-party components

ShtarOS is the project mod. The following independent components are distributed as separate, unmodified JARs in this installer:

- **Rinku 3.0.5** by Keksuccino, based on MCEF by ds58 and CinemaMod contributors. License: LGPL 2.1. Its license text is `licenses/LICENSE_Rinku`. Source: <https://github.com/Keksuccino/Rinku>. The Rinku JAR downloads Java CEF/Chromium native components on first use; its own JAR also contains the relevant JCEF license notice.
- **Fabric API 0.163.0+26.3** by FabricMC. License: Apache 2.0. Its license text is `licenses/LICENSE-fabric-api`. Source: <https://github.com/FabricMC/fabric>.

The installer obtains Python Flask, yt-dlp, and Pillow from their package repositories when needed, and uses a separately installed FFmpeg executable. Their licenses and source are supplied by those projects. The installer does not rename, alter, or claim ownership of Rinku or Fabric API.
