import cv2
import numpy as np

# ====== PARAMETRY ======
MARKER_SIZE = 0.1  # metry (10 cm)

# ====== KAMERA (fake na start) ======
def get_camera_matrix(width, height):
    focal_length = 800
    center = (width / 2, height / 2)

    camera_matrix = np.array([
        [focal_length, 0, center[0]],
        [0, focal_length, center[1]],
        [0, 0, 1]
    ], dtype=np.float32)

    dist_coeffs = np.zeros((4, 1))
    return camera_matrix, dist_coeffs

# ====== ARUCO ======
aruco_dict = cv2.aruco.getPredefinedDictionary(cv2.aruco.DICT_4X4_50)
parameters = cv2.aruco.DetectorParameters()

cap = cv2.VideoCapture(0)

while True:
    ret, frame = cap.read()
    if not ret:
        break

    gray = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY)

    corners, ids, _ = cv2.aruco.detectMarkers(
        gray,
        aruco_dict,
        parameters=parameters
    )

    if ids is not None:
        for i in range(len(ids)):
            marker_id = ids[i][0]

            if marker_id == 2:  # 🔥 interesuje nas ID=2

                # punkty 3D markera
                obj_points = np.array([
                    [-MARKER_SIZE/2,  MARKER_SIZE/2, 0],
                    [ MARKER_SIZE/2,  MARKER_SIZE/2, 0],
                    [ MARKER_SIZE/2, -MARKER_SIZE/2, 0],
                    [-MARKER_SIZE/2, -MARKER_SIZE/2, 0]
                ], dtype=np.float32)

                img_points = corners[i][0]

                h, w = frame.shape[:2]
                camera_matrix, dist_coeffs = get_camera_matrix(w, h)

                success, rvec, tvec = cv2.solvePnP(
                    obj_points,
                    img_points,
                    camera_matrix,
                    dist_coeffs
                )

                if success:
                    # pozycja kamery względem markera
                    R, _ = cv2.Rodrigues(rvec)
                    camera_pos = -R.T @ tvec

                    x, y, z = camera_pos.flatten()

                    # rysowanie
                    cv2.aruco.drawDetectedMarkers(frame, corners)

                    text = f"ID=2 X:{x:.2f} Y:{y:.2f} Z:{z:.2f}"
                    cv2.putText(frame, text, (50, 50),
                                cv2.FONT_HERSHEY_SIMPLEX,
                                1, (0, 255, 0), 2)

    cv2.imshow("ARUCO TRACKING", frame)

    if cv2.waitKey(1) & 0xFF == 27:
        break

cap.release()
cv2.destroyAllWindows()
