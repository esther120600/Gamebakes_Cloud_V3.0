import React, { useState, useEffect } from 'react'
import { useMsal } from "@azure/msal-react"
import { loginRequest } from './componentes/autenticacion/authConfig'

import SeguimientoPedidos from './componentes/seguimiento_pedidos/SeguimientoPedidos'
import ResenasProducto from './componentes/resenas/ResenasProducto'
import logo from './assets/logo_gamebakes.png'
import GestionProductos from './componentes/productos/GestionProductos'
import CatalogoProductos from './componentes/productos/CatalogoProductos'
import DetalleCatalogo from './componentes/productos/DetalleCatalogo'
import Carrito from './componentes/productos/Carrito'
import PerfilUsuario from './componentes/perfil/PerfilUsuario'

import { getAuthData } from './componentes/autenticacion/authUtils'

function App() {
    const { instance, accounts } = useMsal();
    const isMsalAuthenticated = accounts.length > 0;
    const msalAccount = accounts[0];

    const handleLoginMSAL = () => {
        instance.loginPopup(loginRequest).catch(e => console.error("Error en login MSAL:", e));
    };

    const handleLogoutMSAL = () => {
        instance.logoutPopup().catch(e => console.error("Error en logout MSAL:", e));
        sessionStorage.clear();
        setSeccionActiva('inicio');
    };

    const [usuario, setUsuario] = useState(() => {
        const auth = getAuthData();
        if (auth) {
            return { loggedIn: true, rol: auth.rol || 'cliente', id: auth.id || null, nombre: auth.nombre || '' };
        }
        return { loggedIn: false, rol: 'cliente', id: null, nombre: '' };
    });

    useEffect(() => {
        const hash = window.location.hash;
        if (hash && hash.includes('id_token')) {
            const params = new URLSearchParams(hash.substring(1));
            const idToken = params.get('id_token');

            if (idToken) {
                sessionStorage.setItem('token', idToken);

                let nombreGamer = 'Gamer';
                let idCliente = null;
                try {
                    const payload = JSON.parse(atob(idToken.split('.')[1]));
                    nombreGamer = payload.name || payload['cognito:username'] || 'Gamer';
                    idCliente = payload.sub || null;
                } catch (e) {}

                setUsuario({ loggedIn: true, rol: 'cliente', id: idCliente, nombre: nombreGamer });

                window.history.replaceState(null, '', window.location.pathname);
            }
        }
    }, []);

    const handleLoginCognito = () => {
        const cognitoDomain = "https://us-east-10poxmwt2f.auth.us-east-1.amazoncognito.com";
        const clientId = import.meta.env.VITE_COGNITO_CLIENT_ID;
        const redirectUri = window.location.origin;
        window.location.href = `${cognitoDomain}/login?client_id=${clientId}&response_type=token&scope=email+openid+profile&redirect_uri=${redirectUri}`;
    };

    const cerrarSesionManual = () => {
        sessionStorage.clear();
        setUsuario({ loggedIn: false, rol: 'cliente', id: null, nombre: '' });
        setSeccionActiva('inicio');
    };

    // --- 3. ESTADOS COMPARTIDOS ---
    const [productoSeleccionado, setProductoSeleccionado] = useState(null);
    const [seccionActiva, setSeccionActiva] = useState(() => sessionStorage.getItem('seccion') || 'inicio');

    const manejarCambioSeccion = (id) => {
        setSeccionActiva(id || 'inicio');
        sessionStorage.setItem('seccion', id || 'inicio');
        setProductoSeleccionado(null);
    };

    // --- 4. RENDERIZADO NO AUTENTICADO (PANTALLA DE LOGIN LIMPIA) ---
    if (!isMsalAuthenticated && !usuario.loggedIn) {
        return (
            <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', backgroundColor: '#0a0a0a', minHeight: '100vh', padding: '20px' }}>
                <img src={logo} alt="GameBakes" style={{ width: '300px', marginBottom: '50px', filter: 'drop-shadow(0 0 15px rgba(255,255,255,0.1))' }} />

                <div style={{ display: 'flex', gap: '30px', flexWrap: 'wrap', justifyContent: 'center' }}>

                    {/* Tarjeta Cliente (Cognito) */}
                    <div style={{ ...tarjetaLoginStyle, border: '1px solid #00d4ff', boxShadow: '0 10px 25px rgba(0, 212, 255, 0.1)' }}>
                        <h2 style={{ color: '#00d4ff', margin: '0 0 10px 0', textTransform: 'uppercase' }}>Acceso Gamers</h2>
                        <p style={{ color: '#888', fontSize: '0.9rem', marginBottom: '25px', minHeight: '40px' }}>
                            Inicia sesión para explorar el catálogo, revisar tu inventario y realizar pedidos.
                        </p>
                        <button onClick={handleLoginCognito} style={{ ...btnStyle, backgroundColor: '#00d4ff' }}>
                            🎮 Ingresar como Cliente
                        </button>
                    </div>

                    {/* Tarjeta Vendedor (Azure) */}
                    <div style={{ ...tarjetaLoginStyle, border: '1px solid #9b59b6', boxShadow: '0 10px 25px rgba(155, 89, 182, 0.1)' }}>
                        <h2 style={{ color: '#9b59b6', margin: '0 0 10px 0', textTransform: 'uppercase' }}>Acceso Corporativo</h2>
                        <p style={{ color: '#888', fontSize: '0.9rem', marginBottom: '25px', minHeight: '40px' }}>
                            Panel de control exclusivo para administración y gestión de vendedores.
                        </p>
                        <button onClick={handleLoginMSAL} style={{ ...btnStyle, backgroundColor: '#9b59b6', color: 'white' }}>
                            💼 Ingresar con Microsoft
                        </button>
                    </div>

                </div>
            </div>
        );
    }

    // --- 5. RENDERIZADO AUTENTICADO ---
    const rolActual = isMsalAuthenticated ? 'vendedor' : usuario.rol;
    const nombreActual = isMsalAuthenticated ? msalAccount.name : usuario.nombre;
    const idActual = isMsalAuthenticated ? msalAccount.localAccountId : usuario.id;

    const colorCian = '#00d4ff';
    const colorMorado = '#9b59b6';
    const colorTema = rolActual === 'vendedor' ? colorMorado : colorCian;

    const menuCliente = [
        { id: 'inicio', nombre: '🎮 Inicio' },
        { id: 'catalogo', nombre: '🍰 Catálogo' },
        { id: 'carrito', nombre: '🛒 Mi Carrito' },
        { id: 'pedidos', nombre: '📦 Mis Pedidos' },
        { id: 'resenas', nombre: '⭐ Mis Reseñas' },
        { id: 'perfil', nombre: '👤 Mi Perfil' }
    ];

    const menuVendedor = [
        { id: 'inicio', nombre: '🏠 Dashboard' },
        { id: 'productos', nombre: '🧁 Mis Productos' },
        { id: 'pedidos_gestion', nombre: '📋 Gestionar Ventas' },
        { id: 'resenas_gestion', nombre: '💬 Feedback Clientes' },
        { id: 'perfil', nombre: '👤 Mi Perfil' }
    ];

    const menuActual = rolActual === 'vendedor' ? menuVendedor : menuCliente;

    const estiloTarjeta = { backgroundColor: '#111', border: `1px solid #333`, borderBottom: `4px solid ${colorTema}`, borderRadius: '15px', padding: '30px', textAlign: 'center', cursor: 'pointer', boxShadow: '0 4px 15px rgba(0,0,0,0.5)', display: 'flex', flexDirection: 'column', justifyContent: 'center', alignItems: 'center' };

    return (
        <div style={{ display: 'flex', minHeight: '100vh', backgroundColor: '#0a0a0a', color: 'white' }}>
            <nav style={{ width: '250px', backgroundColor: 'rgba(0,0,0,0.9)', borderRight: `2px solid ${colorTema}`, padding: '20px', display: 'flex', flexDirection: 'column', backdropFilter: 'blur(10px)', position: 'fixed', height: '100vh', boxSizing: 'border-box', zIndex: 1000 }}>
                <img src={logo} alt="Logo" style={{ width: '100%', marginBottom: '30px' }} />

                <div style={{ flexGrow: 1, overflowY: 'auto', marginBottom: '20px' }}>
                    <p style={{ color: colorTema, fontSize: '0.8rem', marginBottom: '20px', textAlign: 'center', letterSpacing: '1px' }}>
                        MODO: {rolActual.toUpperCase()}
                    </p>
                    {menuActual.map(item => (
                        <button
                            key={item.id}
                            onClick={() => manejarCambioSeccion(item.id)}
                            style={{ width: '100%', textAlign: 'left', padding: '12px', marginBottom: '10px', backgroundColor: seccionActiva === item.id ? colorTema : 'transparent', color: seccionActiva === item.id ? 'black' : 'white', border: `1px solid ${seccionActiva === item.id ? colorTema : 'rgba(255,255,255,0.1)'}`, borderRadius: '8px', cursor: 'pointer', fontWeight: 'bold', transition: '0.3s' }}
                        >
                            {item.nombre}
                        </button>
                    ))}
                </div>

                <div style={{ paddingBottom: '10px' }}>
                    <button
                        onClick={isMsalAuthenticated ? handleLogoutMSAL : cerrarSesionManual}
                        style={{ width: '100%', backgroundColor: 'transparent', color: '#ff4444', border: '1px solid #ff4444', padding: '12px', borderRadius: '8px', cursor: 'pointer', fontWeight: 'bold', textTransform: 'uppercase', fontSize: '0.8rem' }}
                    >
                        ❌ Cerrar Sesión
                    </button>
                </div>
            </nav>

            <main style={{ flexGrow: 1, padding: '40px', marginLeft: '250px' }}>
                <header style={{ marginBottom: '30px', borderBottom: '1px solid #333', paddingBottom: '10px' }}>
                    <h1 style={{ color: colorTema, margin: 0, textTransform: 'uppercase', letterSpacing: '2px' }}>
                        {(seccionActiva || 'inicio').replace('_', ' ')}
                    </h1>
                </header>

                {seccionActiva === 'pedidos' && <SeguimientoPedidos rol={rolActual} usuarioId={idActual} />}
                {seccionActiva === 'pedidos_gestion' && <SeguimientoPedidos rol={rolActual} usuarioId={idActual} />}
                {seccionActiva === 'resenas' && <ResenasProducto rol={rolActual} usuarioId={idActual} />}
                {seccionActiva === 'resenas_gestion' && <ResenasProducto rol={rolActual} usuarioId={idActual} />}
                {seccionActiva === 'productos' && <GestionProductos vendedorId={idActual}/>}
                {seccionActiva === 'perfil' && <PerfilUsuario usuarioId={idActual} rol={rolActual} />}

                {seccionActiva === 'catalogo' && !productoSeleccionado && (
                    <CatalogoProductos onVerDetalle={(id) => setProductoSeleccionado(id)} />
                )}

                {seccionActiva === 'catalogo' && productoSeleccionado && (
                    <DetalleCatalogo productoId={productoSeleccionado} rol={rolActual} usuarioId={idActual} alVolver={() => setProductoSeleccionado(null)} />
                )}

                {seccionActiva === 'carrito' && (
                    <Carrito usuarioId={idActual} onCambiarSeccion={manejarCambioSeccion} />
                )}

                {seccionActiva === 'inicio' && (
                    <div style={{ animation: 'fadeIn 0.5s' }}>
                        <div style={{ background: `linear-gradient(135deg, rgba(0,0,0,0.8), ${colorTema}33)`, padding: '60px 40px', borderRadius: '20px', border: `1px solid ${colorTema}`, textAlign: 'center', marginBottom: '40px', boxShadow: `0 10px 30px ${colorTema}22` }}>
                            <h1 style={{ fontSize: '3.5rem', margin: '0 0 15px 0', textTransform: 'uppercase', textShadow: `0 0 15px ${colorTema}` }}>
                                ¡SALUDOS, {nombreActual || 'GUERRERO'}!
                            </h1>
                            <p style={{ fontSize: '1.2rem', color: '#ccc', maxWidth: '700px', margin: '0 auto', lineHeight: '1.6' }}>
                                {rolActual === 'vendedor'
                                    ? 'Este es tu panel de control central. Revisa tus pedidos pendientes, gestiona tu armería de productos y domina el mercado.'
                                    : 'Prepárate para subir de nivel con los mejores pasteles y dulces temáticos. ¿Qué aventura gastronómica elegiremos hoy?'}
                            </p>
                        </div>
                    </div>
                )}
            </main>
        </div>
    )
}

const tarjetaLoginStyle = { background: '#111', padding: '40px 30px', borderRadius: '15px', textAlign: 'center', width: '320px', display: 'flex', flexDirection: 'column', alignItems: 'center' };
const btnStyle = { padding: '15px 20px', borderRadius: '8px', border: 'none', cursor: 'pointer', fontWeight: '900', width: '100%', fontSize: '1rem', transition: 'transform 0.2s' };

export default App