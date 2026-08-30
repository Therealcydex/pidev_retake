@echo off
REM ---------------------------------------------------------------------------
REM  Lance l'API de recommandation (FastAPI).
REM
REM  Double-cliquer ce fichier, ou l'executer depuis un terminal.
REM  Laisser la fenetre ouverte : fermer la fenetre arrete l'API.
REM
REM  Le "python" du PATH sous Windows est un raccourci du Microsoft Store qui ne
REM  fonctionne pas. On vise donc explicitement l'interpreteur Anaconda, celui qui
REM  contient fastapi, uvicorn, pandas et scikit-learn.
REM ---------------------------------------------------------------------------

setlocal

REM Se placer a la racine du depot (ce fichier est dans ml\)
cd /d "%~dp0.."

set "PY=%USERPROFILE%\anaconda3\python.exe"
if not exist "%PY%" set "PY=python"

if not exist "ml\modele\recommandation.joblib" (
    echo.
    echo   [ERREUR] Modele introuvable : ml\modele\recommandation.joblib
    echo   Executer d'abord le notebook ml\pidev_retake.ipynb.
    echo.
    pause
    exit /b 1
)

echo.
echo   API de recommandation SkillUp
echo   -----------------------------
echo   API           : http://localhost:8000/recommandations/sante
echo   Documentation : http://localhost:8000/docs
echo   Via gateway   : http://localhost:9090/recommandations/1001?k=5
echo.
echo   Ctrl+C pour arreter.
echo.

"%PY%" -m uvicorn ml.api.main:app --port 8000

endlocal
